package com.kanedias.dybr.exporter;

import java.lang.reflect.Field;

public class Comment {
    public String author_username = "";
    public String dateline = "";
    public String message_html = "";

    protected static Field[] fields = Comment.class.getFields();
}
