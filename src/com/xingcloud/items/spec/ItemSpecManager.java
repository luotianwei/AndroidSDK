package com.xingcloud.items.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import android.util.Log;

import com.xingcloud.utils.DbAssitant;
import com.xingcloud.utils.XingCloudLogger;
/**
 *物品定义管理器 
 * @author chuckzhang
 * 
 */	
public class ItemSpecManager extends AsObject{
	private static ItemSpecManager _instance;
	
	protected HashMap<String,ItemGroup> cachedGroups=new HashMap<String,ItemGroup>();
	protected HashMap<String,ItemSpec> cachedItems=new HashMap<String,ItemSpec>();
	
	public String packages="model.item.itemspec";
	
	/**
	 *获取物品定义管理器实例 
	 * @return 
	 * 
	 */
	public static ItemSpecManager getInstance()
	{
		if(_instance==null)
			_instance=new ItemSpecManager();
		return _instance;
	}

	/**
	 *获取物品定义管理器实例 
	 * @return 
	 * 
	 */
	public static ItemSpecManager instance()
	{
		if(_instance==null)
			_instance=new ItemSpecManager();
		return _instance;
	}
	public void init()
	{
	}
	
	protected ItemSpecManager()
	{
		super();
		_instance=this;
	}
	
	/******************************
	 * 根据groupId生产需要的group数据
	 * @param id
	 * @param deepSearch
	 * @return
	 */
	protected ItemGroup comsiteGroupById(String id,Boolean deepSearch)
	{
		ItemGroup group = new ItemGroup();
		String[] childs = null;
		if(null == id || id.trim().length() <=0)
		{
			XingCloudLogger.log(XingCloudLogger.WARN,"ItemSpecManager->comsiteGroupById : please provide groupId!");
			return null;
		}
		try
		{
			if(!deepSearch)
			{
				group.id = id;
				group.addItem(DbAssitant.instance().getChildItemsByGroupId(id));
				return group;
			}
			else
			{
				childs = DbAssitant.instance().getChildGroupsById(id);
				group.id = id;
				group.addItem(DbAssitant.instance().getChildItemsByGroupId(id));
				if(null == childs || childs.length <=0)
				{
					return group;
				}
				else
				{
					for(int i = 0; i < childs.length;i++)
					{
						group.addItem(comsiteGroupById(childs[i],deepSearch));
					}
				}
			}
		}
		catch(Exception e)
		{
			group = null;
			e.printStackTrace();
		}
		
		return group;
	}
	
	/**
	 *获取特定的物品定义组 
	 * @param groupId
	 * @param recursive
	 * @return 
	 * 
	 */		
	public ItemGroup getGroup(String groupId,Boolean recursive)
	{
		if(cachedGroups.containsKey(groupId))
		{
			return cachedGroups.get(groupId);
		}
		else
		{
			ItemGroup group = comsiteGroupById(groupId,recursive);
			cachedGroups.put(groupId, group);
			return group;
		}
	}
	/**
	 * 获取group组
	 * */
	public ArrayList<ItemGroup> getGroups()
	{
		return this.getGroups(null);
	}
	
	/**
	 * 获取指定组id和type的数据
	 * @param groupId group的id(id如果为null获取所有的group数组，这样占用内存很大且会影响速度，请谨慎使用)
	 * 
	 * @return 数据
	 */
	public ArrayList<ItemGroup> getGroups(String groupId)
	{
		ArrayList<ItemGroup> groups=new ArrayList<ItemGroup>();
		if(groupId!=null)
		{
			groups.add(getGroup(groupId,true));
		}
		else
		{
			String[] groupIds = (DbAssitant.instance().getTopGroups()).split(",");
			for(int i = 0; i < groupIds.length; i++)
			{
				groups.add(getGroup(groupIds[i],true));
			}
		}
		return groups;
	}
	/**
	 * 获取指定id的物品数据
	 * @param itemID 物品id
	 * @return 物品数据
	 */
	public ItemSpec getItem(String itemID)
	{
		return getItem(itemID,null);
	}
	/**
	 * 通过条件查询itemspec 
	 * @param itemID 
	 * @param groupID
	 * @return 
	 * 
	 */		
	public ItemSpec getItem(String itemID,String groupID)
	{
		if(cachedItems.containsKey(itemID))
		{
			return cachedItems.get(itemID);
		}
		else
		{
			ItemSpec item = DbAssitant.instance().getItemSpec(itemID,groupID);
			cachedItems.put(itemID, item);
			return item;
		}
	}
	/**
	 * 获取一组name为_name的物品，name属性对所有item来说是可以重复的。此接口未进行缓存优化
	 * 这个函数从整个item数据库中查询
	 * @param itemName
	 * @return
	 */
	public ArrayList<ItemSpec> getItemsByName(String itemName)
	{
		return this.getItemsByName(itemName, "all");
	}
	/**
	 * 获取制定物品定义组内name为_name的物品()。此接口未进行缓存优化
	 * @param itemName 物品的name
	 * @param groupID 所在组的id(提供group Id可以定位准确，速度更快)
	 *
	 * @return
	 */
	public  ArrayList<ItemSpec> getItemsByName(String itemName,String groupID)
	{
		return DbAssitant.instance().getItemSpecByName(itemName, groupID);
	}
	/**
	 *获得某定义组内元素 。此接口未进行缓存优化
	 * @param groupID
	 * @return 
	 * 
	 */		
	public ArrayList<ItemBase> getItemsInGroup(String groupID){
		return DbAssitant.instance().getChildItemsByGroupId(groupID);
	}
	/**
	 * ItemSpec所在的package路径
	 * */
	public List<String> getPackages()
	{
		if(packages==null || packages.trim().length()==0)
			return new ArrayList<String>();
		
		return Arrays.asList(packages.split(","));
	}
}
