package test;

import com.concur.basesource.anno.Id;
import com.concur.basesource.anno.StaticResource;

/**
 * @description: 测试基础数据DO
 * @author: Jake
 * @create: 2018-05-13 17:34
 **/
@StaticResource
public class ResourcePointConfig {

    @Id
    private int id;

    private int name;

    private String model;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getName() {
        return name;
    }

    public void setName(int name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
