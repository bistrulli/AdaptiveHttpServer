package Server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class TaskThreadFactory implements ThreadFactory {

	SimpleTask task = null;

	public TaskThreadFactory(SimpleTask task) {
		this.task = task;
	}

	// newThread is a factory method
	// provided by ThreadFactory
	public Thread newThread(Runnable command) {
//		while (this.task.getThreadpoolSize() < this.task.getThreadpool().getActiveCount()) {
//			try {
//				TimeUnit.MILLISECONDS.sleep(3);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
		if (this.task.getThreadpoolSize() < this.task.getThreadpool().getActiveCount()) {
			System.out.println("rejected");
			return new Thread();
		} else {
			System.out.println("accepted");
			return new Thread(command);
		}
	}
}