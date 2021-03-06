package com.xingcloud.tasks.services;

import com.xingcloud.core.Config;
import com.xingcloud.core.FileHelper;
import com.xingcloud.core.XingCloud;
import com.xingcloud.event.IEventListener;
import com.xingcloud.language.LanguageManager;

public class LanguageService extends FileService {

	public LanguageService(IEventListener onSuccess,IEventListener onFail) {
		super(onSuccess, onFail);
		this.type = LANGUAGE;
		this.command = Config.LANGUAGE_SERVICE;
	}
	
	public LanguageService() {
		super();
		this.type = LANGUAGE;
		this.command = Config.LANGUAGE_SERVICE;
	}

	public void applyService(Object content)
	{
		LanguageManager.parse(content.toString());
	}
	
	public boolean sendable()
	{
		if(super.sendable())
		{
			return true;
		}
		else
		{
			String content=new String(FileHelper.read(type+"?"+md5+XingCloud.instance().appVersionCode));
			applyService(content);
			
			return false;
		}
	}
}
