package com.xingcloud.tasks.base;

import java.util.Timer;

import android.util.Log;

import com.xingcloud.core.XingCloud;
import com.xingcloud.event.EventDispatcher;
import com.xingcloud.event.ProgressEvent;
import com.xingcloud.utils.XingCloudLogger;

public class Task extends EventDispatcher {
	
	/** @private */
	protected long _completed;
	protected int _currentRetry=0;
	
	//延迟执行时间
	protected int _delay=0;
	private Timer _executeTimer;
	protected Boolean _hasError;

	/** @private */
	protected Boolean _isAborted;
	protected Boolean _isCompleted;
	protected Boolean _paused=true;
	
	/**
	 * 这个命令的描述，会出现在progressPanel上
	 * */
	protected String _progressMsg="";
	// 如果出现错误，重试次数
	protected int _retryCount =0;
	// 任务执行的时间限制
	protected int _timeout =999999;

	protected long _total;
	public Task()
	{
		super();
	}
	
	/**
	 * Creates a new Task instance.
	 * @param delay 延迟多少毫秒执行
	 * @param timeOut 执行时间不能超过多少秒
	 * @param  retryCount 如果出现错误或超时，重试次数
	 */
	public Task(int delay,int timeOut,int retryCount)
	{
		super();
		this._delay=delay;
		this._timeout=timeOut;
		this._retryCount=retryCount;
	}
	/**
	 * Aborts the Task's execution. Any sub-classed implementation needs
	 * to take care of abort functionality by checking the _aborted property.
	 */
	public void abort()
	{
		_isAborted = true;
	}
	/**
	 * 侦听progressEvent事件，为此Task所用
	 * 需要添加侦听器
	 * */
	public void captureProgressEvent(ProgressEvent e)
	{
		this._completed=e.bytesLoaded;
		this._total=e.bytesTotal;
		this.notifyProgress(this);
	}
	/**
	 * Completes the task. This is an abstract method that needs to be overridden
	 * by subclasses. You put code here that should be executed when the task
	 * finishes, like cleaning up event listeners etc. After your code, place a call
	 * to super.complete().
	 * 
	 * @private
	 */
	protected void complete()
	{
		if (!_isAborted) 
			notifyComplete();
		else 
			notifyAbort();
		this.killTimer();
		_currentRetry=0;
	}	
	
	/**
	 * 确实执行之
	 * */
	protected void doExecute()
	{
		_isAborted = false;
		_hasError=false;
		_isCompleted=false;
		_paused=false;
		_completed = 0;
		_total=1;		
		if(this._timeout>0) 
			this.startTimer();
	}
	/**
	 * Executes the Task. In sub-classed Tasks you should override this
	 * method, make a call to super.execute and then initiate all your Task's
	 * execution implementation from here.
	 */ 
	final public void execute()
	{
		if(!_paused) return;
		if(_delay<=0) 
			this.doExecute();
		else {
			Timer t = new Timer();
			t.schedule(new TaskTimer(this), this._delay);
			XingCloudLogger.log(XingCloudLogger.DEBUG,"Task->execute: The task will start "+_delay/1000+" seconds later!");
		}
	}
	/**
	 * The Task's progress.
	 */
	public long getCompleted()
	{
		return _completed;
	}
	public int getDelay() {
		return _delay;
	}
	public String getName()
	{
		return "Task";
	}
	/**
	 * Pauses or unpauses the task.
	 */
	public Boolean getPaused()
	{
		return _paused;
	}
	public String getProgressMsg()
	{
		return _progressMsg;
	}
	/**
	 * Number of times to retry request if it is timing out
	 */
	public int getRetryCount() {
		return _retryCount;
	}
	protected Boolean getRetryOver()
	{
		return this._currentRetry>=_retryCount;
	}
	/**
	 * Timeout in seconds
	 */
	public int getTimeout() {
		return _timeout;
	}	
	
	public long getTotal()
	{
		return _total;
	}
	public Boolean hasError()
	{
		return _hasError;
	}
	
	/**
	 * Gets the abort state of the Task.
	 */
	public Boolean isAborted()
	{
		return _isAborted;
	}
	public Boolean isCompleted()
	{
		return _isCompleted;
	}
	/**
	 * Stops the timer used to manage timeouts and retries
	 */
	protected void killTimer() 
	{
		if(_executeTimer == null) 
			return;
		_executeTimer.cancel();
	}
	/**
	 * Notifies listeners that the task was aborted.
	 * 
	 * @private
	 */
	private void notifyAbort()
	{
		if(XingCloud.instance().progressDialog!=null)
			XingCloud.instance().progressDialog.dismiss();

		_paused=true;
//		hideProgressPanel();
		TaskEvent e = new TaskEvent(TaskEvent.TASK_ABORT,this,"");
		//e.task = this;
		dispatchEvent(e);
		XingCloudLogger.log(XingCloudLogger.DEBUG,"Task->notifyAbort: Task: "+this.toString()+" aborted!");
	}
	private void notifyComplete()
	{
		if(XingCloud.instance().progressDialog!=null)
			XingCloud.instance().progressDialog.dismiss();
		
		_isCompleted=true;
		_paused=true;
//	     hideProgressPanel();
		TaskEvent e = new TaskEvent(TaskEvent.TASK_COMPLETE,this,"");
		//e.task = this;
		dispatchEvent(e);
		XingCloudLogger.log(XingCloudLogger.DEBUG,"Task->notifyComplete: Excute task: "+this.toString()+" successfully!");
	}
	/**
	 * Notifies listeners that an error has occured while executing the TASK.
	 * 
	 * @private
	 * @param errorMsg The error message to be broadcasted with the event.
	 */
	protected void notifyError(String errorMsg,Object data)
	{
		if(XingCloud.instance().progressDialog!=null)
			XingCloud.instance().progressDialog.dismiss();

		if(_hasError) return;
		this._currentRetry++;
		this.killTimer();
		if(!this.getRetryOver()){
			this.doExecute();
			return;	
		}
		_hasError=true;
		_paused=true;
		TaskEvent e = new TaskEvent(TaskEvent.TASK_ERROR, this, errorMsg);
		e.setData(data);
		//e.task = this;
		dispatchEvent(e);
		XingCloudLogger.log(XingCloudLogger.DEBUG,"Task->notifyError: "+errorMsg);
		_currentRetry=0;
	}
	protected void notifyProgress(Task task)
	{
		if(XingCloud.instance().progressDialog!=null)
			XingCloud.instance().progressDialog.dismiss();

		if(task==null)
			task=this;
//        if(this._progressPanelParent&&(command==this)) {
//			ProgressManager.setProgress(_progressPanelParent,command.progressMsg,command.completed,command.total);
//		}
		
		TaskEvent e = new TaskEvent(TaskEvent.TASK_PROGRESS, task,task.getProgressMsg());
		//e.task = this;
		dispatchEvent(e);
	}
	
	/**
	 * If Timer has expired, we have completed all retries.  Kill it.
	 */
	public void onFinalTimeOut() 
	{
		this.killTimer();
		this._currentRetry++;
		if(!this.getRetryOver())
			this.doExecute();
		else
			this.notifyError("This task has retried for "+this._retryCount+" times and failed!",null);
	}
	
	public void setDelay(int d){
		this._delay=d;
	}
	
	
	public void setPaused(Boolean v)
	{
		_paused = v;
	}	
	
	
	
	public void setProgressMsg(String msg)
	{
		this._progressMsg=msg;
	}		
	public void setRetryCount(int r){
		this._retryCount=r;
	}
	
	public void setTimeout(int t){
		this._timeout=t;
	}
	
	protected void startTimer() 
	{
		_executeTimer = new Timer();
		_executeTimer.schedule(new TaskTimer(this),_timeout*1000);
	}
}
