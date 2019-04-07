package com.kanedias.dybr.exporter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Post {
    List<String> tags = new ArrayList<String>();
    List<Comment> comments = new ArrayList<Comment>();
    String no_comments = "0";
    String author_username = "";
    String current_music = "";
    String current_mood = "";
    String access = "0";
    String[] access_list = {};
    String title = "";
    String message_html = "";
    String dateline_date = "";
    String dateline_cdate = "";
    String postid = "";
    Voting voting = new Voting();
    static Field[] fields = Post.class.getFields();

    public static class Voting {
        String question = "";
        boolean multiselect = false;
        boolean end = false;
        List<Answer> answers = new ArrayList<Answer>();
        static Field[] fields = Voting.class.getFields();
    }

    public static class Answer {
        String variant = "0";
        String count = "0";
        String percent = "0";
        static Field[] fields = Answer.class.getFields();

    }
}
