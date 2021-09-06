import numpy as np
import matplotlib.pyplot as plt
import time
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


#tf.compat.v1.disable_eager_execution()

def handler(signum, frame):
    print('Signal handler called with signal', signum)
    subprocess.call(["pkill","-f","SimpleTask","-9"])
    sys.exit(1)


def startSys(initPop,isCpu):
    print("starting")
    if(isCpu):
        return subprocess.Popen([ "sudo","cgexec","-g","cpu:t1",
                                 'java',"-Djava.compiler=NONE",'-jar', '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','0'])
        # return subprocess.Popen([ 'java',"-Djava.compiler=NONE",'-jar', '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
        #                           '--initPop', str(int(initPop)),'--cpuEmu','0'])
        # return subprocess.Popen(["chrt","--rr","99",'java', '-jar', 
        #                     '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
        #                     '--initPop', str(int(initPop)),'--cpuEmu','0'])
        #return subprocess.Popen(["sudo","cgexec","-g","cpu:t1","dd","if=/dev/zero","of=/dev/null"])
    else:
        return subprocess.Popen(['java', '-jar', 
                            '../../target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar', 
                            '--initPop', str(int(initPop)),'--cpuEmu','1'])

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
    quota=np.round(optS[1]*period)
    print(optS[1],int(quota))
    
    # subprocess.check_call(["sudo",'cgset','t1',
    #                   '-r','cpu.cfs_period_us=%d'%(period),
    #                   '-r','cpu.cfs_quota_us=%d'%(int(quota))])
    
    if(croot==None):
        croot = trees.Tree().get_node_by_path('/cpu/t1')
    
    croot.controller.cfs_period_us=period
    croot.controller.cfs_quota_us=int(quota)
    
    
    

keys=["think","e1_bl", "e1_ex"]
proc=None
cpulProc=None
r = redis.Redis()
try:
    for step in tqdm(range(3000)):
        if (step==0): 
            if(proc is not None):
                proc.kill()
                proc=None
                
            proc=startSys(50,True)
            time.sleep(3)
        
        optU=[0,np.random.rand()*10+1] 
        r.set("t1_hw",optU[1])
        print(getstate(r,keys,2))
        setU(optU)
        
        
        time.sleep(0.1)

finally:
    subprocess.call(["pkill","-f","SimpleTask","-9"])