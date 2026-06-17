package com.orangejd.pojo.entity;

public class Category extends BaseEntity {
    private String name;
    private String topicName;
    private String type;
    private Integer sort;
    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
