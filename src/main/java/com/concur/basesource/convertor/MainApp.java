package com.concur.basesource.convertor;

import com.concur.basesource.convertor.contansts.Configurations;
import com.concur.basesource.convertor.ui.MainPanel;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 文件浏览器
 * Created by Jake on 2015/5/31.
 */
public class MainApp {

    /** spring ApplicationContext  */
    private static ApplicationContext applicationContext;

    public static void main(String[] args) {
    	// enable anti-aliasing
    	System.setProperty("awt.useSystemAAFontSettings","on");
    	System.setProperty("swing.aatext", "true");

        // 加载文件配置
        Configurations.loadConfigure();

        new MainApp().init(args);
    }

    // 初始化界面
    private void init(String[] args) {
        applicationContext = new ClassPathXmlApplicationContext("applicationContext-basesource.xml");

        /* 主面板 */
        MainPanel mainPanel = new MainPanel(args);
    }

    /**
     * 获取spring容器
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
