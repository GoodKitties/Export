package com.kanedias.dybr.exporter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Post {
    public List<String> tags = new ArrayList<String>();
    public List<Comment> comments = new ArrayList<Comment>();
    public String no_comments = "0";
    public String author_username = "";
    public String current_music = "";
    public String current_mood = "";
    public String access = "0";
    public String[] access_list = {};
    public String title = "";
    public String message_html = "";
    public String dateline_date = "";
    public String dateline_cdate = "";
    public String postid = "";
    public Voting voting = new Voting();

    static Field[] fields = Post.class.getFields();

    public static class Voting {
        public String question = "";
        public boolean multiselect = false;
        public boolean end = false;
        public List<Answer> answers = new ArrayList<Answer>();

        static Field[] fields = Voting.class.getFields();
    }

    public static class Answer {
        public String variant = "0";
        public String count = "0";
        public String percent = "0";

        static Field[] fields = Answer.class.getFields();

    }
}
