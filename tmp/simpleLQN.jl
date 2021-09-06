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
           dmin>=X[1]-1000*(d)
           dmin>=X[2]-1000*(1-d)
    end)
    return dmin
end

function rate(F,T,h)
    return [(1-F)*T[1,h],
        (1-F)*T[3,h],
        (F)*T[2,h],
        ];
end

jump=[[-1,1,0],
      [1,0,-1],
      [0,-1,1]];

isMIP=true
delta=10^2
dt=10^-2  #time step lento
dt2=0.5*dt #time step veloce
iH=1
oH=parse(Int64, ARGS[3])
H=oH*(1+iH)
#MU=round.([rand()*100,rand()*100],digits=2)
MU=[parse(Float64, ARGS[1]),parse(Float64, ARGS[2])]
#X0=[rand(1:60),rand(1:60),0]
X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
tgt=round((sum(X0)*((1/MU[1])/(1/MU[1]+1/MU[2]))),digits=2)
tgt=tgt
N=3
Nr=3
isStarted=false

model = Model(CPLEX.Optimizer)
set_optimizer_attribute(model, "CPX_PARAM_SCRIND", 0)
set_optimizer_attribute(model, "CPX_PARAM_THREADS", 4)

@variable(model,X[i=1:N,j=0:H]>=0)
@variable(model,T[i=1:Nr,j=0:H]>=0)
@variable(model,E_abs[i=1:H]>=0)
@variable(model,S[i=1:2]>=1)

#@constraint(model,[i=1:N,j=0:H],X[i,j]<=sum(X0))
#@constraint(model,S[1]<=32)
#@constraint(model,S[2]<=32)

@variable(model,D[i=1:2,j=0:H]>=0)
@constraint(model,[h=0:H],D[1,h]<=X[2,h])
@constraint(model,[h=0:H],D[1,h]<=S[1]-X[3,h])
@constraint(model,[h=0:H],D[2,h]<=X[3,h])
@constraint(model,[h=0:H],D[2,h]<=S[2])

#INIT STATE constraint
@constraint(model,X0c[i=1:N],X[i,0]==X0[i])
#Constraints per lo sforzo di controllo
@constraint(model,SC_u[i=1:2],S[i]<=1000);
@constraint(model,SC_l[i=1:2],S[i]>=1);


for h=0:H
    @constraint(model,T[1,h]==MU[1]*X[1,h])
    if(h<=3)
        @constraint(model,T[2,h]==delta*min_([X[2,h],S[1]-X[3,h]],model))
        @constraint(model,T[3,h]==MU[2]*min_([X[3,h],S[2]],model))
    else
        @constraint(model,T[2,h]==delta*D[1,h])
        @constraint(model,T[3,h]==MU[2]*D[2,h])
    end
end

#integration of the fast part
h=0
for oIdx=1:(oH)
    for iIdx=1:iH
        local dy=(jump'*rate(1,T,h))'
        @constraint(model,[i=1:N],X[i,h+1]==dy[i]*dt2+X[i,h])
        global h=h+1;
    end
    #integrate the slow system for one step
    local dy2=(jump'*rate(0,T,h))'
    @constraint(model,[i=1:N],X[i,h+1]==dy2[i]*dt+X[i,h])
    global h=h+1;
end

#add absolute value constraint
for i=1:H
    @constraints(model, begin
           E_abs[i]>=(X[1,i]-tgt)
           E_abs[i]>=-(X[1,i]-tgt)
           #E_abs[i,2]==((X[2,i]+X[3,i])-(sum(X0)-tgt))
    end)
end

for i=0:H
    @constraints(model, begin
           X[3,i]<=S[1]
    end)
end


@objective(model,Min,sum(E_abs)+0.1*sum(S))



X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
prevS=[0,0];
while(true)
    if(isStarted==true)
        set_normalized_rhs(SC_l[1],max(1,prevS[1]-1))
        set_normalized_rhs(SC_l[2],max(1,prevS[2]-1))
        set_normalized_rhs(SC_u[1],min(100,prevS[1]+5))
        set_normalized_rhs(SC_u[2],min(100,prevS[2]+1))
    end
    
    if(isStarted==false)
        global isStarted=true
        set(conn, "ctrl","1")
    end
    for j=1:N
        set_normalized_rhs(X0c[j],X0[j])
    end
    JuMP.optimize!(model)
    local soltime=MOI.get(model, MOI.SolveTime())
    
    println(round.(value.(S)),X0,soltime)
    
    Sopt=Int.(round.(value.(S)))
    global prevS=Sopt

    set(conn, "swthread",@sprintf("%d",Sopt[1]))
    set(conn, "hwthread",@sprintf("%d",Sopt[2]))
    global X0=parse.(Int64, mget(conn, "driver","acquire","exec"))
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
