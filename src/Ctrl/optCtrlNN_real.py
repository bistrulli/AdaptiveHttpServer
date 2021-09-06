'''
Created on 14 mag 2021

@author: emilio
'''
import numpy as np
import matplotlib.pyplot as plt
import time
import tflite_runtime.interpreter as tflite
import os
import scipy.io
from tqdm import tqdm
import casadi
import redis
import subprocess
import signal
from cgroupspy import trees

curpath=os.path.realpath(__file__)
croot=None
period=100000


#tf.compat.v1.disable_eager_execution()

def handler(signum, frame):
    print('Signal handler called with signal', signum)
    subprocess.call(["sudo","pkill","-f","SimpleTask","-9"])
    sys.exit(1)

def mitigateBottleneck(S,X,tgt):
    #devo definire il numero di server da assegnare
    #per andare verso target
    optS=S
    
    #findBootleneck
    U=np.divide(np.minimum(X,S),S)
    b=np.argmax(np.mean(U,axis=0))
    if(b==0):
        return optS
        
    optS[b]=np.minimum(np.maximum(optS[b]+(tgt-X[-1,0]),0),np.sum(X[0,:]))
    
    return optS;

def genAfa():
    #r=np.random.choice([.1,.2,.3,.4,.5,.6,.7,.8,.9,1],p=[0.0182,0.0364,0.0545,0.0727,0.0909,0.1091,0.1273,0.1455,0.1636,0.1818])
    r=np.random.choice([.3,.4,.5,.6,.7,.8,.9,1])
    return np.round(np.random.rand()*0.1+(r-0.1),4)
    #return np.round(np.random.rand()*0.3+0.7,4)

def startSys(initPop,isCpu):
    if(isCpu):
        return subprocess.Popen([ "sudo","cgexec","-g","cpu:t1","--sticky",'java', 
                            "-Djava.compiler=NONE",'-jar', 
                            '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','0'])
    else:
        return subprocess.Popen(['java',"-Xmx4G",'-jar', 
                            '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','1'])

def setU(optS):
    
    global croot,period
    quota=np.round(optS[1]*period)
    #print(optS[1],int(quota))
    
    # subprocess.check_call(["sudo",'cgset','t1',
    #                   '-r','cpu.cfs_period_us=%d'%(period),
    #                   '-r','cpu.cfs_quota_us=%d'%(int(quota))])
    
    if(croot==None):
        croot = trees.Tree().get_node_by_path('/cpu/t1')
    
    #croot.controller.cfs_period_us=period
    croot.controller.cfs_quota_us=int(quota)

def resetU():
    global croot, period
    if(croot==None):
        croot = trees.Tree().get_node_by_path('/cpu/t1')
        
    croot.controller.cfs_period_us=period
    croot.controller.cfs_quota_us=int(-1)
    
def setUCpuLimit(P,optS,attached):
    if(not attached):
        Pc=subprocess.Popen(["sudo","cpulimit","-p","%s"%(P.pid),"-i",
                           "-l","%s"%(2400),"-f","/root/limit.txt"])
        print(Pc.pid)
        return Pc
    else:
        lfile=open("/root/limit.txt","w+")
        lfile.write(str(int(np.round(optS[1]*100))))
        lfile.close()
        subprocess.call(["sudo","kill","-SIGUSR1","%s"%(P.pid)])

def getstate(r,keys,N):
    state=[float(r.get(keys[i])) for i in range(len(keys))]
    astate=[state[0]]
    gidx=1;
    for i in range(1,N):
        astate.append(state[gidx]+state[gidx+1])
        gidx+=3
    return astate

class optCtrlNN2:
    model = None
    stateVar = None
    Uvar = None
    tfmodel=None
    Xtrain=None
    Utrain=None
    Ytrain=None
    stdx=None
    stdy=None
    stdu=None
    meanx=None
    meany=None
    meanu=None
    pVarMatrix=None
    
    def __init__(self, modelPath,train_data):
        #self.tfmodel=load_model(modelPath)
        self.tfmodel= tflite.Interpreter(model_path=modelPath,num_threads=1)
        
        #questo lo devo sostituire e rendere piu pulito
        DST=scipy.io.loadmat(train_data);

        self.Xtrain=DST['DS_X']
        self.Utrain=DST['DS_U']
        self.Ytrain=DST['DS_Y']
        
        self.stdx=np.std(self.Xtrain,0);
        self.stdy=np.std(self.Ytrain,0);
        self.stdu=np.std(self.Utrain,0);
        self.meanx=np.mean(self.Xtrain,0);
        self.meany=np.mean(self.Ytrain,0);
        self.meanu=np.mean(self.Utrain,0);
        
        
    def buildOpt(self,X0,tgt,MU,S,P,Sold_N,H,isAR=False):
        
        input_N=(X0-self.meanx)/self.stdx
        if(Sold_N is not None):
            Sold=Sold_N*self.stdu+self.meanu
        else:
            Sold=None
        
        lb=[np.sum(X0)-1]
        ub=[np.sum(X0)+1]
        for i in range(1,P.shape[0]):
            lb.append(10**(-1))
            ub.append(15)
        
        for i in range(P.shape[0]*P.shape[1]):
            lb.append(0)
            ub.append(10)
        
        lb=np.matrix(lb).reshape([1,-1])
        ub=np.matrix(ub).reshape([1,-1])
        
        Sin=None
        if(Sold_N is None):
            Sin=np.matrix([np.sum(X0),1,1,0,1,0,0,0,1,1,0,0])
        else:
            Sin=np.matrix(Sold_N)
        
        self.tfmodel.allocate_tensors()
        
        input_details = self.tfmodel.get_input_details()
        output_details = self.tfmodel.get_output_details()
        
        self.tfmodel.set_tensor(input_details[0]['index'], np.float32(input_N))
        self.tfmodel.set_tensor(input_details[1]['index'], np.float32(Sin))
        
        self.tfmodel.invoke()
        #Ypredicted_N=self.tfmodel({'inputx':input_N,'inputu':Sin})
        #Ypredicted_N=[0,np.zeros([1,2,38]),np.zeros([1,10])]
        
        # print(self.tfmodel.get_tensor(output_details[0]['index']).shape)
        # print(self.tfmodel.get_tensor(output_details[1]['index']).shape)
        # print(self.tfmodel.get_tensor(output_details[2]['index']).shape)
        
        Bias = self.tfmodel.get_tensor(output_details[0]['index'])
        Gain = self.tfmodel.get_tensor(output_details[2]['index'])

        # Bias=Ypredicted_N[-1]
        # Gain=Ypredicted_N[1]
        
        model = casadi.Opti()
        Uvar = model.variable(1,self.Xtrain.shape[1]+self.Xtrain.shape[1]*self.Xtrain.shape[1]);
        stateVar = model.variable(self.Xtrain.shape[1],H);
        absE_var = model.variable(1,H);
        
        model.subject_to(absE_var>=0)
        
        uvar_dn=Uvar*self.stdu.reshape([1,-1])+self.meanu.reshape([1,-1])
        model.subject_to(uvar_dn>=lb)        
        model.subject_to(uvar_dn<=ub)
        
        c=np.array([0,0,0,0,1,0,0,0,1,1,0,0])
        model.subject_to(uvar_dn[self.Xtrain.shape[1]:]==c[self.Xtrain.shape[1]:].reshape([1,-1]))
        model.subject_to(uvar_dn[0]==np.sum(X0))
        
        N=self.Xtrain.shape[1]
    
        #in questo modo scrivo il controllore
        if(isAR):
            for h in range(H-1):
                if(h==0):
                    model.subject_to(stateVar[:,h+1]==Bias[0,(N*h):(N*h+N)]+Gain[0,:,0:N*(N+1)]@Uvar[0,:].T)
                else:
                    model.subject_to(stateVar[:,h+1]==(Bias[0,(N*h):(N*h+N)]+(Gain[0,:,N*(N+2)*(h-1)+N*(N+1):N*(N+2)*(h)+N*(N+1)]@casadi.horzcat(Uvar,stateVar[:,h].T).T)))
        else:
            Ypredicted=(Bias.T+Gain[0,:,:]@Uvar[0,:].T)
            for h in range(H):
                #model.subject_to(stateVar[:,h+1]==Bias[0,(3*h):(3*h+3)]+Gain[0,(3*h):(3*h+3),:]@Uvar[0,:].T)
                model.subject_to(stateVar[:,h]==Ypredicted[(N*h):N*(h+1)])
        
        obj=0
        for h in range(H):
            model.subject_to(absE_var[0,h]>=(stateVar[0,h]*self.stdy[h*N]+self.meany[h*N]-tgt))
            model.subject_to(absE_var[0,h]>=-(stateVar[0,h]*self.stdy[h*N]+self.meany[h*N]-tgt))
            #obj+=(stateVar[0,h]*self.stdy[h*N]+self.meany[h*N]-tgt)**2
            obj+=absE_var[0,h]
        
        ru=0;
        if(Sold is not None):
            for ui in range(1,P.shape[0]):
                ru+=(uvar_dn[ui]-Sold[ui])**2
                
        
        model.minimize(obj+0.1*ru+0.1*casadi.sumsqr(uvar_dn[1:]))
        
        optionsIPOPT={'print_time':False,'ipopt':{'print_level':0}}
        optionsOSQP={'print_time':False,'osqp':{'verbose':False}}
        model.solver('ipopt',optionsIPOPT)
        #model.solver('osqp',optionsOSQP)
        model.solve()
        return model.value(Uvar),model.value(stateVar[:,1])

if __name__ == "__main__":
    
    signal.signal(signal.SIGINT, handler)
    
    curpath=os.path.realpath(__file__)
    ctrl = optCtrlNN2("%s/../../learnt_model/long/model_3tier.tflite"%(os.path.dirname(curpath)),
                     "%s/../../learnt_model/long/open_loop_3tier_H5.mat"%(os.path.dirname(curpath)))
    
    isAR=True
    isCpu=False
    dt=10**(-1)
    H=5
    N=3
    rep=1
    sTime=500
    TF=sTime*rep*dt;
    Time=np.linspace(0,TF,int(np.ceil(TF/dt))+1)
    XSNN=np.zeros([N,len(Time)])
    XSSIM=np.zeros([N,XSNN.shape[1]])
    XSSIM2=np.zeros([N,XSNN.shape[1]])
    XSSIMPid=np.zeros([N,XSNN.shape[1]])
    #XSSIM[:,0]=np.random.randint(low=1,high=100,size=[1,N])
    XSNN[:,0]=XSSIM[:,0]
    XSSIM2[:,0]=XSSIM[:,0]
    optSNN=np.zeros([N,XSNN.shape[1]])
    optSMD=np.zeros([N,XSNN.shape[1]])
    optSPID=np.zeros([N,XSNN.shape[1]])
    P = np.matrix([[0, 1.,0],[0,0,1.],[1., 0,0]])
    S = np.matrix([-1, -1,-1]).T
    MU = np.matrix([1,10,10]).T
    Us=np.zeros([N,XSNN.shape[1]])
    sIdx=[]
    keys=["think","e1_bl", "e1_ex","t1_hw","e2_bl", "e2_ex","t2_hw"]
    
    alfa=[];
    ek=0
        
    #alfa=np.round(np.random.rand(),4)
    #tgt=alfa[-1]*0.441336*np.sum(XSSIM[:,0])
    tgtStory=[0]
    #init_cstr=["X%d_0" % (i) for i in range(P.shape[0])];
    cp=-1
    proc=None
    cpulProc=None
    r = redis.Redis()
    
    Ie=None
    
    try:
            for step in tqdm(range(XSSIM.shape[1]-1)):
                #compute ODE
                if step==0 or step%sTime==0: 
                    Sold=None       
                    alfa.append(genAfa())
                    #alfa.append(0.2)
                    #XSSIM[:,step]=[np.random.randint(low=15,high=100),0,0]
                    XSSIM[:,step]=[90,0,0]
                    #print(alfa[-1],XSSIM[:,step])
                    print(XSSIM[:,step])
                    XSSIM2[:,step]=XSSIM[:,step]
                    XSSIMPid[:,step]=XSSIM[:,step]
                    S[0] = np.sum(XSSIM[:,step])
                    tgt=np.round(alfa[-1]*0.8257*np.sum(XSSIM[:,step]),5)
                    sIdx.append({'alfa':alfa[-1],'x0':XSSIM[:,step].tolist(),"tgt":tgt})
                    optSPid=[np.sum(XSSIM[:,step]),1,1]
                    cp+=1
                    ek=0
                    Ie=0
                    
                    if(proc is not None):
                        subprocess.call(["sudo","pkill","-f","SimpleTask","-9"])
                        proc.kill()
                        proc=None
                
                if step==0 or step%sTime==0:
                    if(isCpu):
                        resetU()
                    r.set("t1_hw",np.sum(XSSIM[:,step]))
                    r.set("t2_hw",np.sum(XSSIM[:,step]))
                    proc=startSys(np.sum(XSSIM[:,step]),isCpu)
                    time.sleep(2)
                    #cpulProc=setUCpuLimit(proc,None,False)
                
                XSSIM[:,step]=getstate(r,keys,N)
                
                if(step>0):
                    Ie+=(tgt-XSSIM[0,step])
                
                stime=time.time()
                optU_N,XNN=ctrl.buildOpt(XSSIM[:,[step]].T,tgt+0.05*Ie,MU,S,P,Sold,H,isAR)
                ftime=time.time()-stime
                
                optU=optU_N*ctrl.stdu+ctrl.meanu
                #optU[1]=np.maximum(optU[1]+0.01*Ie,0.01)
                #print(optU[0:N])
                Sold=optU_N
                
                
                #print(XSSIM[:,[step]].T,optU[1])
                r.set("t1_hw",optU[1])
                r.set("t2_hw",optU[2])
                #print(XSSIM[:,step],tgt)
                #setUCpuLimit(cpulProc,optU,True)
                if(isCpu):
                    setU(optU)
                #print(optU)
                            
                optSNN[:,step]=optU[0:N]
                # optSMD[:,step]=optS2
                tgtStory+=[tgt]
                
                time.sleep(0.3)
                
                # optSPID[:,step]=optSPid
                # optSPid=mitigateBottleneck(optSPid, Xsim3, tgt)
             
            #print("NN Reference error %f%% \nODE Reference error %f%% \n"%(np.abs(XSNN[0,-1]-tgt)*100/tgt,np.abs(XSODE[0,-1]-tgt)*100/tgt))
            plt.close('all')    
            
            xsim_cavg=[]
            xsim_cavg2=[]
            xsim_cavg3=[]
            e=[]
            e2=[]
            e3=[]
            for i in range(len(sIdx)):
                xsim_cavg+=np.divide(np.cumsum(XSSIM[:,i*sTime:(i+1)*sTime],axis=1),np.arange(1,sTime+1)).T[:,0].tolist()
                xsim_cavg2+=np.divide(np.cumsum(XSSIM2[:,i*sTime:(i+1)*sTime],axis=1),np.arange(1,sTime+1)).T[:,0].tolist()
                xsim_cavg3+=np.divide(np.cumsum(XSSIMPid[:,i*sTime:(i+1)*sTime],axis=1),np.arange(1,sTime+1)).T[:,0].tolist()
                e.append(np.abs(xsim_cavg[-1]-tgtStory[i*sTime+1])/tgtStory[i*sTime+1])
                e2.append(np.abs(xsim_cavg2[-1]-tgtStory[i*sTime+1])/tgtStory[i*sTime+1])
                e3.append(np.abs(xsim_cavg3[-1]-tgtStory[i*sTime+1])/tgtStory[i*sTime+1])
                
                sIdx[i]["e"]=e[-1]*100;
        
            # f=plt.figure()
            # plt.title("Mean Relaive Long-run Tracking Error (%)")
            # plt.boxplot([np.array(e)*100,np.array(e2)*100,np.array(e3)*100])
            # plt.xticks([1,2,3],['NN','MD','PID'])
            
            f=plt.figure()
            plt.title("Mean Relaive Long-run Tracking Error (%)")
            plt.boxplot([np.array(e)*100])
            plt.xticks([1],['NN'])
            plt.savefig("boxerror.png")
            
            sumPop=[]
            tgts=[]
            for ipop in sIdx:
                sumPop.append(np.sum(ipop["x0"]))
                tgts.append(np.sum(ipop["tgt"]))
            
            plt.figure()
            plt.title("error vs total pop")
            plt.scatter(sumPop, np.array(e)*100)
            plt.savefig("evspop.png")
            
            plt.figure()
            plt.title("error vs alfa")
            plt.scatter(alfa, np.array(e)*100) 
            plt.savefig("evsalfa.png")
            
            plt.figure()
            plt.title("error vs tgt")
            plt.scatter(tgts, np.array(e)*100) 
            plt.savefig("evstgt.png")
        
            
            print(np.array(e)*100)
            #print(np.array(e2)*100)
            #print(np.array(e3)*100)
            #print(sIdx)
                
            for k in range(0,1):
                plt.figure()
                plt.plot(Time,XSSIM.T[:,k],label="SIM_NNctrl")
                #plt.plot(Time,XSSIM2.T[:,k],label="SIM_MDctrl",linestyle ='-.')
                #plt.axhline(y = tgt, color = 'r', linestyle = '--')
                plt.plot(Time,np.array(tgtStory),'--',color = 'r')
            plt.legend()
            plt.savefig("queue_sim.png")
            
            plt.figure()
            plt.plot(xsim_cavg,label="SIM_NNctrl")
            #plt.plot(xsim_cavg2,label="SIM_MDctrl",linestyle ='-.')
            plt.plot(np.array(tgtStory),'--',color = 'r')
            plt.legend()
            plt.savefig("queue_avg.png")
            
            plt.figure()
            plt.stem(alfa)
            
            plt.figure()
            plt.title("Control Signals NN")
            for i in range(1,N):
                plt.plot(optSNN[i,:].T,label="Tier_%d"%(i))
            plt.legend()
            # plt.figure()
            # plt.title("Control Singals Model Driven")
            # plt.plot(optSMD[1:,:].T)
            # plt.figure()
            # plt.title("Control Singals PID")
            # plt.plot(optSPID[1:,:].T)
            
            
            print(np.mean(optSPID[1:,:],axis=1))
            print(np.mean(optSMD[1:,:],axis=1))
            print(np.mean(optSNN[1:,:],axis=1))         
    
            plt.show()
    
    finally:
        subprocess.call(["sudo","pkill","-f","SimpleTask","-9"])