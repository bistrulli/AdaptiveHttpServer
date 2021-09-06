import numpy as np
import scipy.io as sp
import os
import redis
import time
import subprocess
import matplotlib.pyplot as plt
from tqdm import tqdm
import signal
import sys
import uuid
from pathlib import Path
from sys import platform
from cgroupspy import trees

croot=None

def handler(signum, frame):
    print('Signal handler called with signal', signum)
    #subprocess.call(["sudo","pkill","-f","SimpleTask","-9"])
    stopSystem()
    sys.exit(1)
    
    
def startSys(initPop,isCpu):
    if(isCpu):
        return subprocess.Popen([ "sudo","cgexec","-g","cpu:t1","--sticky",'java', 
                            "-Djava.compiler=NONE",'-jar', 
                            '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','0'])
    else:
        return subprocess.Popen(['java', '-jar', 
                            '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','1'])
        
def stopSystem():
    subprocess.call(["sudo","pkill","-9","-f","SimpleTask"])
    


def genAfa():
    #r=np.random.choice([.1,.2,.3,.4,.5,.6,.7,.8,.9,1],p=[0.0182,0.0364,0.0545,0.0727,0.0909,0.1091,0.1273,0.1455,0.1636,0.1818])
    r=np.random.choice([.3,.4,.5,.6,.7,.8,.9,1])
    return np.random.rand()*0.1+(r-0.1)

def getTr():
    #rob=[0.0182,0.0364,0.0545,0.0727,0.0909,0.1091,0.1273,0.1455,0.1636,0.1818]
    #prob=np.exp(range(10,0,-1))/np.sum(np.exp(range(10,0,-1)))
    prob=np.exp(np.linspace(5,1,10))/np.sum(np.exp(np.linspace(5,1,10)))
    r=np.random.choice([.1,.2,.3,.4,.5,.6,.7,.8,.9,1],p=prob)
    return np.random.rand()*0.1+(r-0.1)

def generate_random_integers(_sum, n, variance=None):  
    mean = _sum / n
    if(variance==None):
        variance = int(3 * mean)

    min_v = 0
    max_v = mean + variance
    array = [min_v] * n

    diff = _sum - min_v * n
    while diff > 0:
        a = np.random.randint(0, n)
        if array[a] >= max_v:
            continue
        array[a] += 1
        diff -= 1
    return array

def randfixsum(samples, sum_to , range_list):
    assert range_list[0]<range_list[1], "Range should be a list, the first element of which is smaller than the second"
    arr = np.random.rand(samples)
    sum_arr = sum(arr)

    new_arr = np.array([int((item/sum_arr)*sum_to) if (int((item/sum_arr)*sum_to)>range_list[0]and int((item/sum_arr)*sum_to)<range_list[1]) \
                            else np.random.choice(range(range_list[0],range_list[1]+1)) for item in arr])
    difference = sum(new_arr) - sum_to
    while difference != 0:
        if difference < 0 :
                for idx in np.random.choice(range(len(new_arr)),abs(difference)):
                    if new_arr[idx] != range_list[1] :
                        new_arr[idx] +=  1

        if difference > 0:
                for idx in np.random.choice(range(len(new_arr)), abs(difference)):
                    if new_arr[idx] != 0 and new_arr[idx] != range_list[0] :
                        new_arr[idx] -= 1
        difference = sum(new_arr) - sum_to
    return np.matrix(list(map(int, new_arr)))


def mitigateBottleneck(S,X,tgt):
    #devo definire il numero di server da assegnare
    #per andare verso target
    optS=np.zeros(S.shape)
    for i in range(S.shape[1]):
        optS[0,i]=S[0,i]
    
    #findBootleneck
    eps=np.ones(S.shape)*10**(-5)
    U=np.divide(np.minimum(X,S),S)
    b=np.argmax(U)
    b=1
    
    
    optS[0,b] = np.minimum(np.maximum(optS[0,b]+(tgt-X[0,0])*0.6,0.05),np.sum(X))
    
    print(optS)
    for i in range(1,optS.shape[1]):
        optS[0,i]=np.max([optS[0,i]+np.random.choice([1,-1])*np.random.rand()*0.2*optS[0,i],10**(-3)])
        
    print(optS)
   
    return optS

def getstate(r,keys,N):
    state=[float(r.get(keys[i])) for i in range(len(keys))]
    astate=[state[0]]
    gidx=1;
    for i in range(1,N):
        astate.append(state[gidx]+state[gidx+1])
        gidx+=3
    return astate

def setU(optS):
    
    global croot
    
    period=100000
    quota=np.round(optS[0,1]*period)
    #print(optS[0,1],int(quota))
    
    if(croot==None):
        croot = trees.Tree().get_node_by_path('/cpu/t1')
    
    croot.controller.cfs_period_us=period
    croot.controller.cfs_quota_us=int(quota)

signal.signal(signal.SIGINT, handler)

repcount=0;

#per npoints intendo il numero di diverso di stati iniziali che considero
rep=100
H=5
ssTime=(H+1)*30
N=3
npoints=ssTime*(rep)
DS_X=np.zeros([npoints//(H+1),N])
DS_U=np.zeros([npoints//(H+1),N*(N+1)])
DS_Y=np.zeros([npoints//(H+1),N*H])
XS=np.zeros([npoints,N]);
X0=None

keys=["think","e1_bl", "e1_ex","t1_hw","e2_bl", "e2_ex","t2_hw"]

r = redis.Redis()

point=0;
optS=None
P=None
tgt=None
proc=None
tgtstory=[]
X0=None
myuuid = uuid.uuid4()

fname="open_loop_3tier_H5"

try:
    for tick in tqdm(range(npoints),ascii=True):
        if((np.mod(tick,ssTime)==0) or tick==0):
            
            #salvo risultati intermedi
            if(tick!=0):
                Path("../../data/%s/"%(str(myuuid)) ).mkdir( parents=True, exist_ok=True )
                sp.savemat("../../data/%s/%s.mat"%(myuuid,fname),{"DS_X":DS_X,"DS_Y":DS_Y,"DS_U":DS_U})
            
            XS[tick,:]=[np.random.randint(low=1,high=100)]+[0]*(N-1)
            X0=XS[[tick],:]
           
            optS=np.round(np.matrix([np.sum(X0),getTr()*14.8+0.2,getTr()*14.8+0.2]),4)
            r.set("t1_hw",str(optS[0,1]))
            r.set("t2_hw",str(optS[0,2]))
            #setU(optS)
            
            if(proc is not None):
                croot=None
                #proc.kill()
                stopSystem()
            
            proc=startSys(np.sum(XS[tick,:]),False)
            time.sleep(10);
            
            #get fake P
            P=np.random.rand(N,N);
            P=P/np.sum(P,1,keepdims=True);
            
            alfa=genAfa()
            tgt=alfa*0.8980*np.sum(XS[tick,:]);
            
            XS[tick,:]=getstate(r,keys,N)
            X0=XS[[tick],:]
            
            
            
        else:
            XS[tick,:]=getstate(r,keys,N)
            tgtstory.append(tgt)
            if(np.mod(tick+1,H+1)==0 and tick>=H):
                DS_X[point,:]=XS[tick+1-(H+1)]
                DS_U[point,0:optS.shape[1]]=optS
                DS_U[point,optS.shape[1]:]=P.flatten()
                DS_Y[point,:]=np.reshape(XS[tick-(H-1):tick+1],[1,N*H])
                point+=1
                
                # print(XS)
                # print(DS_X)
                # print(DS_U)
                # print(DS_Y)
                
                #get fake P
                P=np.random.rand(N,N);
                P=P/np.sum(P,1,keepdims=True);
                
                #gen rnd S
                optS=np.round(np.matrix([np.sum(X0),getTr()*14.8+0.2,getTr()*14.8+0.2]),4)
                
                #nel caso open loop qui genero tutto random
                #optS=mitigateBottleneck(optS,XS[[tick],:],tgt)
                r.set("t1_hw",str(optS[0,1]))
                r.set("t2_hw",str(optS[0,2]))
                #setU(optS)
        time.sleep(0.5)
    proc.kill()
    r.close()
    
    #salvo risultati intermedi
    
    Path("../../data/%s/"%(str(myuuid)) ).mkdir( parents=True, exist_ok=True )
    sp.savemat("../../data/%s/%s.mat"%(myuuid,fname),{"DS_X":DS_X,"DS_Y":DS_Y,"DS_U":DS_U})
    
    cavg=[]
    e=[]
    for i in range(rep):
        cavg+=np.divide(np.matrix(np.cumsum(XS[(i*ssTime):((i+1)*ssTime),0],axis=0)),np.matrix(np.arange(1,ssTime+1))).tolist()[0]
        #e.append(np.abs(xsim_cavg[-1]-tgtStory[i*sTime+10])/tgtStory[i*sTime+10])
    
    #cavg=np.divide(np.cumsum(XS[:,0],axis=0),np.arange(1,npoints+1))

    plt.figure()
    plt.plot(XS[:,0])
    plt.plot(cavg)
    #plt.plot(tgtstory,linestyle="--")
    plt.show()
    
    # print(np.abs(cavg[-1]-tgt)*100/tgt)
    # print(DS_Y.shape)
finally:
    stopSystem()
