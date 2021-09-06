clear
files=dir('../../target/data/Ddctrl/*.mat');

data=load(sprintf('%s/%s',files(1).folder,files(1).name));
X=zeros([size(data.XS),length(files)]);
for i=1:length(files)
    disp(files(i).name)
    data=load(sprintf('%s/%s',files(i).folder,files(i).name));
    X(:,:,i)=data.XS;
end
Xm=mean(X,3);
figure;
hold on;
plot(Xm(:,1));
plot(Xm(:,end))
% plot(sum(Xm(:,[2,3]),2));
%ylim([0,sum(Xm(1,:))*1.3]);

mean(Xm(:,1))