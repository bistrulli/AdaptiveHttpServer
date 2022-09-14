package monitoring;

public class rtSample {
	private Long start=null;
	private Long end=null;
	private Integer qlen=null;
	
	public rtSample(Long start,Long end, Integer qlen) {
		this.start=start;
		this.end=end;
		this.qlen=qlen;
	}
	
	public rtSample(Long start,Long end) {
		this.start=start;
		this.end=end;
	}
	
	public Long getStart() {
		return this.start;
	}
	
	public void setStart(Long start) {
		this.start = start;
	}
	
	public Long getEnd() {
		return this.end;
	}
	public void setEnd(Long end) {
		this.end = end;
	}
	
	public Long getRT() {
		return this.end-this.start;
	}

	public Integer getQlen() {
		return qlen;
	}

	public void setQlen(Integer qlen) {
		this.qlen = qlen;
	}
	
}
