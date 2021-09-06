clear
data_files=dir("/Users/emilio/Desktop/data_exp/exp_nomins_fast_0.3_nocpu");
XS=zeros(10000,5,size(data_files,1));
idx=[];
for i=1:size(data_files,1)
    if(contains(data_files(i).name,".mat"))
        idx=[idx,i];
        data=load(sprintf("%s/%s",data_files(i).folder,data_files(i).name));
        XS(:,:,i)=data.XS(:,:,:);
        disp(mean(mean(data.XS(:,:,:),3)))
    end
end
Pop=sum(XS(1,1:3,idx(1)));

alfa=0.3;

XM=mean(mean(XS(1500:end,:,idx),3));
tgt=alfa*Pop*(1/2)/(1/2+1/10);
fprintf("Avg_queue=%.3f, tgt=%.3f, err(%%)=%.3f\n",...
        XM(1),tgt,abs(XM(1)-tgt)*100/tgt);

figure
plot(mean(XS(:,1:3,idx),3));
yline(tgt,'--','ref');
