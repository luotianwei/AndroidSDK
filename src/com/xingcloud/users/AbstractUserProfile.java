package com.xingcloud.users;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import com.xingcloud.core.ModelBase;
import com.xingcloud.core.XingCloud;
import com.xingcloud.event.CollectionEvent;
import com.xingcloud.event.IEventListener;
import com.xingcloud.event.PropertyChangeEvent;
import com.xingcloud.event.XingCloudEvent;
import com.xingcloud.items.owned.ItemsCollection;
import com.xingcloud.items.owned.OwnedItem;
import com.xingcloud.items.spec.AsObject;
import com.xingcloud.social.services.UserInfo;
import com.xingcloud.tasks.net.Remoting;
import com.xingcloud.tasks.services.ServiceManager;
import com.xingcloud.users.auditchanges.AuditChangeManager;
import com.xingcloud.users.services.LoadProfileService;
import com.xingcloud.users.services.PlatformLoginService;
import com.xingcloud.utils.XingCloudLogger;

/**
 * Profile相当于是一款游戏的‘总线’，具体提供以下一些功能支持
 * 1，管理着用户的核心信息，包括 数据信息（通过属性体现），物品信息（通过ownedItem来体现）
 * 2，提供基本操作ownedItem数组的方法
 * 3，监控Profile的整个变化，封装出一整套事件，参见ProfileChangeEvent，便于开发人员围绕Profile做各种扩展和使用
 * 4，提供生成随机用户数据的功能，便于Debug.FALSE = true的本地化测试，在FriendDelegate里面会涉及用到profile的这个功能来生成随机的好友列表
 * 
 * 在XingCloud构架中，我们要求大家把ProfileManager.instance.profile做成整个系统的数据中心，界面元素采用Listener的模式来监听Profile发生的变化，
 * 控制逻辑直接修改Profile的属性，而不是控制逻辑一边修改Profile的属性同时还要负责修改界面元素。界面元素可以直接通过监听Profile的属性变化来同步界面。
 * 另外，Profile里面的ownedItem抽象了表示一个玩家所拥有的各种物品，奖励，信件，礼物。程序员可以把这组ownedItem当成一个表，通过各种各样的方式来查询出所要的ownedItem。
 * 例如，我们要查询用户的获得的奖励，我们可以遍历所有的ownedItem查询每一个ownedItem是不是AchievementOwnedItem类型，如果是，那么就返回出来。
 * 
 * 又例如，我们在农场里面要获得到当前 用户使用的dog，以前的方式是专门定义了一个叫做”currentActiveDogItem“的属性，那么最好的方法就是，
 * 直接通过查询ownedItem，寻找是狗类别的ownedItem，并且如果这个ownedItem是active的那么就返回这个。
 *	
 * 添加、删除ownedItem都应该通过addOwnedItem和removeOwnedItem方法，而不应该在ownedItems中直接进行。如果直接进行则不会将此操作传送到后台。
 * 修改profile和OwnedItem的属性，则应该通过changeProfileProtery和changeOwnedItem方法来进行。否则不能将操作写入到后台。
 * 
 * 前台对用户敏感数据（如金钱、经验等）的操作，在往后台传输的时候，要传输修改之后的值。但是此值是用来后台验证的，并非是后台直接使用。
 * 对于用户名等的修改，则后台可以直接使用。
 * 一般典型的修改敏感数据的情景是：用户做了某个操作（如买卖东西等），修改了用户的金钱。由于所有操作是按照序列发送到后台的，所以对这种操作，后台只需要验证，在进行了
 * 前面的操作之后，当时的金钱是否和用户传回来的相符合即可。不需要做其他操作。
 * 
 * 对于送礼之类的操作（既修改其他用户物品的操作），建议前台和后台采用不同方向的ownedItem自增方式（如前台每增加一个物品，id自减1，而后台每增加一个物品，id自增1.）
 * 这样可以避免在未同步的情况下，出现物品id冲突的情况。
 * 对于像雇佣，修改其他用户金钱的操作，建议改成给红包等操作，需要玩家打开红包，才获得钱。尽量避免异步操作的问题。
 * 
 * EXAMPLE:
 *   AuditChange.startAudit("BuyItem",{itemId:2003,count=3},successCallBack,failCallBack);
 *   profile.propOwnedItem.addItem(....);
 *   profile.exp+=10;
 *   profile.coins-=200; 
 */	
public class AbstractUserProfile extends ModelBase {

	private int _itemsLoaded = 0;
	
	protected List<UserInfo> userInfo;
	
	public List<UserInfo> getUserInfo() {
		return userInfo;
	}

	public void setUserInfo(List<UserInfo> userInfo) {
		this.userInfo = userInfo;
	}
	
	protected int level = 0;
	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		if(this.level!=level)
		{
			dispatchEvent(new PropertyChangeEvent("level",this.level,level, this));
			this.level = level;
		}
	}

	public int getCoin() {
		return coin;
	}

	public void setCoin(int coin) {
		if(this.coin!=coin)
		{
			dispatchEvent(new PropertyChangeEvent("coin",this.coin,coin, this));
			this.coin = coin;
		}
	}

	public int getMoney() {
		return money;
	}

	public void setMoney(int money) {
		if(this.money!=money)
		{
			dispatchEvent(new PropertyChangeEvent("money",this.money,money, this));
			this.money = money;
		}
	}

	public int getExperience() {
		return experience;
	}

	public void setExperience(int experience) {
		if(this.experience!=experience)
		{
			dispatchEvent(new PropertyChangeEvent("experience",this.experience,experience, this));
			this.experience = experience;
		}
	}
	protected int coin = 0;
	protected int money = 0;
	protected int experience = 0;
	
	protected Boolean isOwner;
	/**
	 * 用户物品实例集合
	 */		
	protected ArrayList itemsBulk=new ArrayList();
	
	protected IEventListener loadFailCallback;
	protected IEventListener loadOkCallback;
	
	IEventListener onItemsLoaded=new IEventListener() {
		
		public void performEvent(XingCloudEvent evt) {
			_itemsLoaded++;
			
			if(_itemsLoaded==itemsBulk.size())
				dispatchEvent(new XingCloudEvent(XingCloudEvent.ITEMS_LOADED,AbstractUserProfile.this));
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};
	IEventListener  onItemsLoadedError=new IEventListener() {
		
		public void performEvent(XingCloudEvent evt) {
			dispatchEvent(new XingCloudEvent(XingCloudEvent.ITEMS_LOADED_ERROR,AbstractUserProfile.this));
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};

	private IEventListener onLoginFailed = new IEventListener(){
		public void performEvent(XingCloudEvent evt) {
			onDataUpdateFail(evt);
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};
	private IEventListener onLoginSuccess = new IEventListener(){
		public void performEvent(XingCloudEvent evt) {

			Remoting rem = (Remoting)evt.getTarget();
			
			Object result = rem.response.getData();
			if(result==null)
			{
				onDataUpdateFail(evt);
				return;
			}
			
			/*
			if(result instanceof ArrayList)
			{
				ArrayList data = (ArrayList)result;
				if(data.size()>0)
					onDataUpdated(evt,(AsObject)(data.get(0)));
			}
			else if(result instanceof AsObject)
			{
				onDataUpdated(evt,(AsObject)result);
			}
			*/
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};
	
	private IEventListener onLoadFailed = new IEventListener(){
		public void performEvent(XingCloudEvent evt) {
			onDataUpdateFail(evt);
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};
	private IEventListener onLoadSuccess = new IEventListener(){
		public void performEvent(XingCloudEvent evt) {
			Remoting rem = (Remoting)evt.getTarget();
			
			Object result = rem.response.getData();
			if(result==null)
			{
				onDataUpdateFail(evt);
				return;
			}
			
			if(loadOkCallback!=null)
				loadOkCallback.performEvent(evt);
			/*
			if(result instanceof ArrayList)
			{
				ArrayList data = (ArrayList)result;
				if(data.size()>0)
					onDataUpdated(evt,(AsObject)(data.get(0)));
			}
			else if(result instanceof AsObject)
			{
				onDataUpdated(evt,(AsObject)result);
			}
			*/
		}

		@Override
		public void prePerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postPerformEvent(XingCloudEvent evt) {
			// TODO Auto-generated method stub
			
		}
	};
	
	/**
	 * 一个UserProfile可能包含多种ownedItem
	 * */		
	public ItemsCollection ownedItems=new ItemsCollection();


	/**
	 * @param isOwner 是否是玩家自己
	 * */
	public AbstractUserProfile(Boolean isOwner/*false*/)
	{
		this.isOwner=isOwner;
		if(this.isOwner){
			if(XingCloud.changeMode)
			{
				this.addEventListener(PropertyChangeEvent.PROPERTY_CHANGE,new IEventListener() {
					public void performEvent(XingCloudEvent evt) {
						((AbstractUserProfile)evt.getTarget()).onPropertyChanged((PropertyChangeEvent)evt);
					}

					@Override
					public void prePerformEvent(XingCloudEvent evt) {
						// TODO Auto-generated method stub
						
					}

					@Override
					public void postPerformEvent(XingCloudEvent evt) {
						// TODO Auto-generated method stub
						
					}
				});
			}
			
			if(XingCloud.autoLogin)
			{
				ServiceManager.instance().send(new PlatformLoginService(this,onLoginSuccess,onLoginFailed));
			}

		}
	}
	
	/**
	 * 添加一个ownedItems和ownedItem的映射，IDE自动完成
	 * @param name: items字段的名字，如ownedItems
	 * @param itemType: items对应的ownedItem类名，全路径
	 * */
	protected void addCollection(String name,String itemType)
	{   
		ItemsCollection items = null;
		try {
			items = (ItemsCollection)getProperty(name);
			itemsBulk.add(items);
		} catch (Exception e) {
			XingCloudLogger.log(XingCloudLogger.DEBUG,"Can not find ItemColletion with name : "+name);
			return;
		}
		
		if(items==null)
		{
			XingCloudLogger.log(XingCloudLogger.DEBUG,"ItemColletion is empty with name : "+name);
			return;
		}
		
		items.itemType=itemType;
		items.OwnerProperty=name;
		items.owner=this;
		if(this.isOwner) 
			if(XingCloud.changeMode)
			{
				items.addEventListener(CollectionEvent.COLLECTION_CHANGE,new IEventListener() {
					
					public void performEvent(XingCloudEvent evt) {
						((ItemsCollection)evt.getTarget()).owner.onItemChanged((CollectionEvent)evt);
					}

					@Override
					public void prePerformEvent(XingCloudEvent evt) {
						// TODO Auto-generated method stub
						
					}

					@Override
					public void postPerformEvent(XingCloudEvent evt) {
						// TODO Auto-generated method stub
						
					}
				});
			}
		XingCloudLogger.log(XingCloudLogger.VERBOSE,"addItemToMap : " + name);
	}
	
	public void addItem(OwnedItem item)
	{
		ItemsCollection items =(ItemsCollection)this.getProperty(item.ownerProperty);
		items.addItem(item);
	}
	
	/**
	 * 创建ownedItems和ownedItem的映射，IDE自动生成
	 * */
	protected void createItemCollection()
	{
		this.addCollection("ownedItems","com.xingcloud.items.owned.OwnedItem");
	}
	
	public Boolean getIsOwner()
	{
		return this.isOwner;
	}
	
	public void load(IEventListener successCallback,IEventListener failCallback)
	{
		this.loadOkCallback=successCallback;
		this.loadFailCallback=failCallback;
		ServiceManager.instance().send(new LoadProfileService(this,onLoadSuccess,onLoadFailed));
	}
	
	protected void onDataUpdated(XingCloudEvent evt,AsObject userinfo)
	{
		evt.setCurrentTarget(this);
		
		this.parseFromObject(userinfo, null);
		
		this.dispatchEvent(new XingCloudEvent(XingCloudEvent.PROFILE_LOADED,this));
		if(this.loadOkCallback!=null)
			this.loadOkCallback.performEvent(evt);
		
		if(XingCloud.autoLoadItems)
		{
			updateUserItems();
		}
		else
		{
			this.dispatchEvent(new XingCloudEvent(XingCloudEvent.ITEMS_LOADED,this));
		}
	}
	protected void onDataUpdateFail(XingCloudEvent evt)
	{
		evt.setCurrentTarget(this);
		
		if(this.loadFailCallback!=null) 
			this.loadFailCallback.performEvent(evt);
		
		this.dispatchEvent(new XingCloudEvent(XingCloudEvent.PROFILE_LOAD_ERROR,null));
	}
	
	/**
	 * 物品变化侦听
	 * e.currentTarget和 e.target均是ItemsCollection
	 * e.items       add,remove时是发生事件的元素，update时是一组PropertyChangeEvent
	 * e.kind        add ,update,remove
	 * e.location    111 ,-1    ,111
	 * e.oldLocation -1  ,-1    ,-1
	 * */
	public void onItemChanged(CollectionEvent e)
	{
		ArrayList items=e.items;
		int max=items.size();
		for(int i=0;i<max;i++){
			Object item=items.get(i);
			switch(e.kind){
				case CollectionAdd:
					AuditChangeManager.instance().appendItemAddChange((ModelBase)item);
					break;
				case CollectionUpdated:
					PropertyChangeEvent propEvt=(PropertyChangeEvent)item;
					AuditChangeManager.instance().appendUpdateChange(propEvt.source,propEvt.property.toString(),propEvt.oldValue,propEvt.newValue);
					break;
				case CollectionRemove:
					AuditChangeManager.instance().appendItemRemoveChange((ModelBase)item);
					break;
			}				
		}
	}
	public void onPropertyChanged(PropertyChangeEvent evt)
	{
		AuditChangeManager.instance().appendUpdateChange((AbstractUserProfile)evt.getTarget(),evt.property,
				evt.oldValue,
				evt.newValue);
	}
	

	public void updateUserData(AsObject data)
	{
		if(data==null)
		{
			this.dispatchEvent(new XingCloudEvent(XingCloudEvent.PROFILE_LOAD_ERROR,this));
			this.dispatchEvent(new XingCloudEvent(XingCloudEvent.ITEMS_LOADED_ERROR,this));
			return;
		}
		
		parseFromObject(data,null);
		this.dispatchEvent(new XingCloudEvent(XingCloudEvent.PROFILE_LOADED,this));
		
		if(XingCloud.autoLoadItems)
		{
			updateUserItems();
		}
		else
		{
			this.dispatchEvent(new XingCloudEvent(XingCloudEvent.ITEMS_LOADED,this));
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.xingcloud.core.ModelBase#parseFromObject(com.xingcloud.items.spec.AsObject, java.util.ArrayList)
	 */
	public void parseFromObject(AsObject data,ArrayList<String> excluded)
	{
		AuditChangeManager.instance().stopTrack();
		
		ArrayList excludeProps = new ArrayList();
		int itemsSize = itemsBulk.size();
		for(int i=0;i<itemsSize;i++)
		{
			ItemsCollection item = (ItemsCollection)(itemsBulk.get(i));
			if(item!=null)
			{
				excludeProps.add(item.OwnerProperty);
			}
		}
		
		if(excluded!=null)
			excludeProps.addAll(excludeProps);
		
		super.parseFromObject(data,excludeProps);
	}
	public void removeItem(OwnedItem item)
    {
    	ItemsCollection items =(ItemsCollection)this.getProperty(item.ownerProperty);
		items.removeItem(item);
    }
    public void updateItem(OwnedItem item)
    {
    	ItemsCollection items =(ItemsCollection)this.getProperty(item.ownerProperty);
		items.updateItem(item);
    }
    /**
	 * 在Userprofile发生变化后更新物品数据
	 * 
	 */		
	public void updateUserItems()
	{
		for (int i = 0; i < itemsBulk.size(); i++) 
		{
			ItemsCollection items=(ItemsCollection)itemsBulk.get(i);

			items.load(onItemsLoaded,onItemsLoadedError);
		}
		
	}
}
