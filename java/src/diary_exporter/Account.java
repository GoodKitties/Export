package diary_exporter;

import java.lang.reflect.Field;

public class Account {
    public String userid = "";
    public String username = "";
    public String shortname = "";
    public String journal = "";
    public String profile_access = "0";
    public String journal_access = "0";
    public String comment_access = "0";
    public String by_line = "";
    public String birthday = "00-00-00";
    public String sex = "";
    public String education = "";
    public String sfera = "";
    public String about = "";
    public String timezone = "0";
    public String country = "";
    public String city = "";
    public String journal_title = "";
    public String epigraph = "";
    public String avatar = "";
    public String[] profile_list = {};
    public String[] journal_list = {};
    public String[] comment_list = {};
    public String[] white_list = {};
    public String[] black_list = {};
    public String[] tags = {};
    public String[] favourites = {};
    public String[] readers = {};
    public String[] communities = {};
    public String[] members = {};
    public String[] owners = {};
    public String[] moderators = {};
    protected static Field[] fields = Account.class.getFields();
    
    public Account() {
        
    }
}
