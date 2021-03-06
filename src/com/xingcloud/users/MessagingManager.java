package com.xingcloud.users;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

import com.xingcloud.core.XingCloud;
import com.xingcloud.event.IEventListener;
import com.xingcloud.event.XingCloudEvent;
import com.xingcloud.items.spec.AsObject;
import com.xingcloud.tasks.base.TaskEvent;
import com.xingcloud.tasks.net.Remoting;
import com.xingcloud.tasks.net.Remoting.RemotingMethod;
import com.xingcloud.utils.XingCloudLogger;

public class MessagingManager {
	/**
	 * 所有已经发送过的message批次，都按batchId存入一个字典，方便处理返回结果，甚至在出现错误是恢复游戏状态
	 * 以batchId为索引的一批数组，每个数组里按顺序存放了发送过的action，方便进行处理
	 * */
	protected static HashMap<String, ArrayList<RemotingMessage>> messageBatches=new HashMap<String, ArrayList<RemotingMessage>>();
	/**
	 * message队列达到这个长度后就会向服务器发送
	 * */
	public static int minLength=3;
	/**
	 * message发送的最短时间周期,毫秒
	 * */
	public static int minPeriod=1000;

	/**
	 * 是否开启消息队列模式。该模式会等待上一次请求结果返回后再进行下一次请求
	 */
	public static boolean enableQueued = false;
	/**
	 * 在消息队列模式下，是否在发生错误后继续发送剩余未发送出去的
	 */
	public static boolean enableQueuedContinueOnError = true;
	private boolean messageReceived = true;

	/**
	 * 服务路径
	 * */
	protected String _mainService;
	/**
	 * 在队列中待发的message
	 * */
	protected ArrayList<RemotingMessage> _messages=new ArrayList<RemotingMessage>();

	protected boolean _paused = false;

	protected Boolean _started=false;

	protected Timer timer;

	/**
	 * 批量添加消息。此接口会首先将之前遗留的消息全部发送出去，再将这些新添加消息发送出去。
	 * @param messages 消息队列
	 */
	synchronized public void addMessage(ArrayList<RemotingMessage> messages)
	{
		synchronized(_messages)
		{
			this.send();
			this.stop();
			for(RemotingMessage message:messages)
			{
				if(this._messages.indexOf(message)>-1) 
					continue;

				this._messages.add(message);
			}
			this.start();
		}
	}

	/**
	 * 添加一个消息
	 * @param message 消息
	 */
	synchronized public void addMessage(RemotingMessage message)
	{
		synchronized(_messages)
		{
			if(this._messages.indexOf(message)>-1) 
			{
				XingCloudLogger.log(XingCloudLogger.WARN, "MessagingManager->addMessage : duplicated message "+message.name+", will be ignored.");
				return;
			}

			this._messages.add(message);

			if((!_paused) && ((this._messages.size()>=minLength) /*|| (SFSManager.instance().isActive())*/))
			{
				doExecute();
			}
			else
				this.start();
		}
	}

	synchronized public void clean()
	{
		this.stop();
		messageBatches.clear();
		synchronized(_messages)
		{
			_messages.clear();
		}
	}

	synchronized protected void doExecute()
	{
		synchronized(_messages)
		{

			if(_messages.size()==0 || (enableQueued && !messageReceived))
				return;
			//this.stop();
			messageReceived = false;

			RemotingMethod method = RemotingMethod.POST;
			/*
		if(Config.getConfig(XingCloud.SFS_ENABLE_CONFIG)!=null)
		{
			boolean isSFSEnabled = (Boolean)Config.getConfig(XingCloud.SFS_ENABLE_CONFIG);
			if(isSFSEnabled)
				method = RemotingMethod.SFS;
		}
			 */
			Remoting rem=new Remoting(_mainService,null,method,XingCloud.needAuth);

			//每批audit发送到后台的结构为
			//  =>id
			//  =>info {uid:,XA_targname:,plateform_sig_api_key:},用于后台log或其它用途
			//  =>data: 一组{index:0,name:,changes:[],params:{}}
			AsObject params=new AsObject();
			ArrayList data = new ArrayList();
			params.setProperty("data", data);

			//每执行一批次就将其放入字典
			ArrayList batch=new ArrayList();
			messageBatches.remove(rem.getCurrentRemotingID());
			messageBatches.put(String.valueOf(rem.getCurrentRemotingID()),batch);

			for(int i=0;i<_messages.size();i++){
				if(enableQueued && i>=minLength)
					break;

				RemotingMessage msg=_messages.get(i);
				if(msg.sended) 
					continue;
				AsObject msgParams = new AsObject();
				msgParams.setProperty("index", i);
				msg.appendMessage(msgParams);
				data.add(msgParams);
				msg.sended=true;
				batch.add(msg);
			}

			rem.setParams(params);

			rem.addEventListener(TaskEvent.TASK_COMPLETE, new IEventListener() {

				public void performEvent(XingCloudEvent evt) {
					evt.getTarget().removeEventListener(TaskEvent.TASK_COMPLETE, this);
					evt.getTarget().removeEventListener(TaskEvent.TASK_ERROR, this);
					handleBatchResult((Remoting)evt.getTarget(),((Remoting)evt.getTarget()).response.getData());
				}

				@Override
				public void prePerformEvent(XingCloudEvent evt) {
				}

				@Override
				public void postPerformEvent(XingCloudEvent evt) {
					if((enableQueued && messageReceived))
						doExecute();
				}
			});
			rem.addEventListener(TaskEvent.TASK_ERROR, new IEventListener() {

				public void performEvent(XingCloudEvent evt) {
					evt.getTarget().removeEventListener(TaskEvent.TASK_COMPLETE, this);
					evt.getTarget().removeEventListener(TaskEvent.TASK_ERROR, this);
					handleBatchResult((Remoting)evt.getTarget(),null);
				}

				@Override
				public void prePerformEvent(XingCloudEvent evt) {
				}

				@Override
				public void postPerformEvent(XingCloudEvent evt) {
					if(enableQueued && enableQueuedContinueOnError && messageReceived)
						doExecute();
				}
			});

			rem.execute();
			//发完清空
			_messages.removeAll(batch);
			//_messages.clear();
		}
	}

	/**
	 * 发送信息队列
	 */
	public void send()
	{
		doExecute();
	}

	/**
	 * 获取已经发送过的某个id的message
	 * */
	synchronized public  RemotingMessage getRemotingMessage(int id)
	{
		Iterator<ArrayList<RemotingMessage>> it = messageBatches.values().iterator();
		while(it.hasNext())
		{
			ArrayList<RemotingMessage> batch = it.next();
			for(RemotingMessage msg:batch)
			{
				if(msg.id==id)
					return msg;
			}
		}
		return null;
	}
	/**
	 * 处理一组AuditChange成功返回的结果
	 * data
	 *     --id                 这组message的编号，和发送时对应
	 *     --code
	 *     --message
	 *     --data                一组结果，每个元素对应一个message的结果,Array
	 *            --index
	 *            --code
	 *            --message
	 *            --data
	 * */
	synchronized protected void handleBatchResult(Remoting remoting,Object data)
	{
		messageReceived = true;

		ArrayList<RemotingMessage> auditBatch=messageBatches.get(String.valueOf(remoting.getCurrentRemotingID()));
		if(auditBatch==null)
		{
			onBatchFail("Not exist batch id "+remoting.getCurrentRemotingID());
		}
		else
		{
			boolean success = true;
			ArrayList dataArr = null;
			if(data==null || !(data instanceof ArrayList))
			{
				success = false;
			}
			else
			{
				dataArr = (ArrayList)data;

				if(auditBatch.size()!=dataArr.size())
				{
					success= false;				
				}
			}

			for(int i=0;i<auditBatch.size();i++){
				RemotingMessage msg = auditBatch.get(i);
				if(success)
				{
					AsObject remotingResult = (AsObject) dataArr.get(i);
					msg.handleDataBack(new MessagingEvent(remotingResult));
				}
				else
				{
					MessagingEvent msg_evt = new MessagingEvent(null);
					msg_evt.setData(data);
					if(remoting.response!=null)
					{
						msg_evt.code=remoting.response.getCode();
						msg_evt.message=remoting.response.getMessage();
					}

					msg.handleDataBack(msg_evt);
				}

			}
			auditBatch.clear();
			messageBatches.remove(auditBatch);
		}
	}
	/**
	 * 初始化ActionManager的后台服务，并创建一个ActionManager实例
	 * @param service ActionManager在后台的服务，详见XINGCLOUD.ACTION_SERVICE
	 * */
	public void init(String service)
	{
		if(service==null)
			throw new Error("Please give me a backend RemotingManager service!");

		_mainService=service;

		if(Remoting.defaultGateway==null) 
			XingCloudLogger.log(XingCloudLogger.WARN,"ActionManager->init: The gateway for this game is null!");
	}

	synchronized protected void onBatchFail(String error)
	{
		XingCloudLogger.log(XingCloudLogger.DEBUG,"MessagingManager.onBatchFail --> " + error);
	}

	/**
	 * 移除消息队列
	 * @param messages
	 */
	synchronized public void removeMessage(ArrayList<RemotingMessage> messages)
	{
		synchronized(_messages)
		{
			for(RemotingMessage message:messages)
			{
				int index = this._messages.indexOf(message);
				if(index>-1)
					this._messages.remove(message);
			}
		}
	}

	/**
	 * 移除一条消息
	 * @param message
	 */
	synchronized public void removeMessage(RemotingMessage message)
	{
		synchronized(_messages)
		{
			if(this._messages.indexOf(message)>-1) 
				return;

			this._messages.remove(message);
		}
	}

	/**
	 * 清空
	 * */
	synchronized public void reset()
	{
		messageBatches.clear();
	}

	/**
	 * 开始进程，自动向后台发送队列中的message，一般不需要手动启动，只要有message推入队列，它便可以启动
	 * */
	synchronized public void start()
	{
		if(enableQueued)
		{
			doExecute();
		}
		else
		{
			if(_started==true) 
				return;
			_started=true;

			if(timer==null)
				timer = new Timer();
			timer.schedule(new TimerTask() {

				/*
				 * (non-Javadoc)
				 * @see java.util.TimerTask#run()
				 */
				public void run() {
					if(_paused )
						return;
					if(_messages==null || _messages.size()==0)
						return;
					doExecute();
				}
			}, 0,minPeriod);
		}
	}

	/**
	 * 退出进程，停止向后台发送message
	 * */
	synchronized public void stop()
	{
		if(_started==false) return;
		_started=false;
		timer.cancel();
		timer = null;
	}

	synchronized public void pause()
	{
		_paused=true;
	}

	synchronized public void resume()
	{
		_paused = false;
	}
}
