using JuMP,CPLEX,Statistics,MAT,Redis,Printf

#connect to redi
conn = RedisConnection()

function min_(X,model)
    if(length(X)!=2)
        error("supperted only two sided minimum")
    end
    d=@variable(model,binary = true)
    dmin=@variable(model)
    @constraints(model, begin
           dmin<=X[1]
           dmin<=X[2]
           dmin>=X[1]-100*(d)
           dmin>=X[2]-100*(1-d)
    end)
    return dmin
end

function rate(F,T,h)
    return [(1-F)*T[1,h],
        (1-F)*T[3,h],
        (F)*T[2,h],
        ];
end

function buildCtrl(jump,delta,dt,dt2,iH,oH,H,maxS,MU,X0,tgt,N,Nr)

    model = Model(CPLEX.Optimizer)
    set_optimizer_attribute(model, "CPX_PARAM_SCRIND", 0)
    set_optimizer_attribute(model, "CPX_PARAM_THREADS", 1)
    
    @variable(model,X[i=1:N,j=0:H]>=0)
    @variable(model,T[i=1:Nr,j=0:H]>=0)
    @variable(model,E_abs[i=1:H]>=0)
    #@variable(model,E_abs>=0)
    @variable(model,avgX>=0)
    @variable(model,S[i=1:2]>=0)
    
    @constraint(model,S[1]<=maxS[1])
    @constraint(model,S[1]>=1)
    @constraint(model,S[2]<=maxS[2])
    @constraint(model,X.<=sum(X0))
    
    @variable(model,D[i=1:2,j=0:H]>=0)
    @constraint(model,[h=0:H],D[1,h]<=X[2,h])
    @constraint(model,[h=0:H],D[1,h]<=S[1]-X[3,h])
    @constraint(model,[h=0:H],D[2,h]<=X[3,h])
    @constraint(model,[h=0:H],D[2,h]<=S[2])
    
    #INIT STATE constraint
    @constraint(model,X0c[i=1:N],X[i,0]==X0[i])
    #Constraints per lo sforzo di controllo
    @constraint(model,SC_u[i=1:2],S[i]<=maxS[i]);
    @constraint(model,SC_l[i=1:2],S[i]>=1);
    
    
    for h=0:H
        @constraint(model,T[1,h]==MU[1]*X[1,h])
        if(h<=H)
            @constraint(model,T[2,h]==delta*min_([X[2,h],S[1]-X[3,h]],model))
            @constraint(model,T[3,h]==MU[2]*min_([X[3,h],S[2]],model))
        else
            @constraint(model,T[2,h]==delta*D[1,h])
            @constraint(model,T[3,h]==MU[2]*D[2,h])
        end
    end
    
    local h=0
    for oIdx=1:(oH)
        for iIdx=1:iH
            dy=(jump'*rate(1,T,h))'
            @constraint(model,[i=1:N],X[i,h+1]==dy[i]*dt2+X[i,h])
            h=h+1;
        end
        #integrate the slow system for one step
        dy2=(jump'*rate(0,T,h))'
        @constraint(model,[i=1:N],X[i,h+1]==dy2[i]*dt+X[i,h])
        h=h+1;
    end
    
    #avg constraint
    @constraint(model,avgX==(1.0/H)*sum(X[1,:]))
    
    #add absolute value constraint
    for i=1:H
        @constraints(model, begin
               E_abs[i]>=(X[1,i]-tgt)
               E_abs[i]>=-(X[1,i]-tgt)
        end)
    end 
  
    #@constraints(model, begin
    #    E_abs>=(avgX-tgt)
    #    E_abs>=-(avgX-tgt)
    #end)
    
    for i=0:H
        @constraints(model, begin
               X[3,i]<=S[1]
        end)
    end
    
    
    @objective(model,Min,sum(E_abs))
    return [model,SC_l,SC_u,X0c,S]
end

jump=[[-1,1,0],
      [1,0,-1],
      [0,-1,1]];
delta=10^2
dt=0.3*10^-2  #time step lento
dt2=0.5*dt #time step veloce
iH=1
oH=parse(Int64, ARGS[3])
maxS=[40,40];
H=oH*(1+iH)
MU=[parse(Float64, ARGS[1]),parse(Float64, ARGS[2])]
X0=[20,0,0]
alfa=parse(Float64, ARGS[4])
tgt=round((sum(X0)*((1/MU[1])/(1/MU[1]+1/MU[2]))),digits=4)
tgt=tgt*alfa
N=3
Nr=3


isStarted=false
objOpt=buildCtrl(jump,delta,dt,dt2,iH,oH,H,maxS,MU,X0,tgt,N,Nr)

model=objOpt[1]
SC_l=objOpt[2]
SC_u=objOpt[3]
X0c=objOpt[4]
S=objOpt[5]


X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
prevS=[0,0]
while(true)
    isAppRunning=get(conn, "app_running")
    if(isAppRunning=="1")
        if(isStarted==true)
            set_normalized_rhs(SC_l[1],max(1,prevS[1]-2))
            set_normalized_rhs(SC_l[2],max(1,prevS[2]-10))
            #set_normalized_rhs(SC_u[1],min(maxS[1],prevS[1]+10)
            #set_normalized_rhs(SC_u[2],min(maxS[2],prevS[2]+1))
        end
        
        if(isStarted==false)
            global isStarted=true
            set(conn, "ctrl","1")
        end
        for j=1:N
            set_normalized_rhs(X0c[j],X0[j])
        end
        
        JuMP.optimize!(model)
        
        if(termination_status(model)==MOI.OPTIMAL)
            local soltime=MOI.get(model, MOI.SolveTime())
        
            Sopt=Int.(round.(value.(S)))
            #Sopt=[Int(round(value(S[1]))),value(S[2])]
            
            println(Sopt,X0,soltime,tgt)
            
            global prevS=Sopt
            
            set(conn, "swthread",@sprintf("%d",Sopt[1]))
            set(conn, "hwthread",@sprintf("%.5f",Sopt[2]))
        else
            println(termination_status(model), X0)
        end
       
        global X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
    else
        println("App stopped")
        global X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
        global isStarted=false
        global prevS=[0,0]
        global objOpt=buildCtrl(jump,delta,dt,dt2,iH,oH,H,maxS,MU,X0,tgt,N,Nr)
        global model=objOpt[1]
        global SC_l=objOpt[2]
        global SC_u=objOpt[3]
        global X0c=objOpt[4]
        global S=objOpt[5]
    end
end

#Controlled layered three-tier system by varying #threads HW/SW at each tier. Simulation (i.e., 100 runs) Vs Target reguirement.
#println(mean(mean(XS[:,:,:],dims=3)[end-100:end,1])," ",abs(mean(mean(XS[:,:,:],dims=3)[end-100:end,1])-tgt)*100/tgt," ",mean(stimes)," ",maximum(stimes))
#println(mean(mean(Shw,dims=3),dims=1))
# plot(t,mean(XS[:,:,:],dims=3)[:,:],label = "Controlled-simulation",legend=false)
# hline!([tgt],label = "Target",reuse=true,legend=false)
# xlabel!("Time(s)")
# ylabel!("QueueLength")




#p=plot(t,mean(Shw,dims=3)[:,:],reuse = false,title="SW/HW Core")

# matwrite("LP_LQNMPC.mat", Dict(
# 	"XS" => XS,
# 	"NT" => Tsw,
#     "NC" => Shw,
#     "Time"=>stimes
# ); compress = false)
