package diary_exporter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Json_to_html implements Runnable {
    static NewJFrame frame;
    String account;
    List<String> posts;
    String username = "";
    
    public Json_to_html(NewJFrame f) {
        frame = f;
    }
    
    
    public void getReady(String dir) {
        File[] fList;        
        File F = new File(dir);

        fList = F.listFiles();
        
        for (File fList1 : fList) {
            if (!fList1.isFile()) {
                continue;
            }
            String name = fList1.getName();
            if(name.equals("account.json")) {
                account = "account.json";
                
            }
            if(!name.contains("posts_")) continue;
            posts.add(name);
        }        
    }
    

    @Override
    public void run() {
        account = "";
        posts = new ArrayList();
        
        String dir = frame.getHtmlDir();
        
        dir = dir.replaceAll("\\\\", "/");
        
        if(!dir.equals("")) {
            getReady(dir);
            if(!account.equals("")) {
                File myPath;
                myPath = new File(dir+"_html");
                myPath.mkdirs();
                
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject object;
                    JSONObject acc = (JSONObject) parser.parse(new FileReader(dir+"/"+account));
                    
                    String account_page = accountText(acc, "html_"+posts.get(0)+".html");
                    try (FileWriter file = new FileWriter(dir + "_html/html_account.html")) {
                        file.write(account_page.replaceAll("\\\\/", "/"));
                    }
                    for(int i = 0; i < posts.size(); i++) {
                        String p = posts.get(i);
                        object = (JSONObject) parser.parse(new FileReader(dir+"/"+p));
                        int prev = i-1<0 ? posts.size()-1 : i-1;
                        int next = i+1>=posts.size() ? 0 : i+1;
                        String page = pageText(object, "html_" + posts.get(prev) + ".html", "html_" + posts.get(next) + ".html", (String) acc.get("username"));
                        try (FileWriter file = new FileWriter(dir + "_html/html_" + p + ".html")) {
                            file.write(page.replaceAll("\\\\/", "/"));
                        }
                    }
                    
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Json_to_html.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Json_to_html.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ParseException ex) {
                    Logger.getLogger(Json_to_html.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        frame.changeHtmlEnabled();
        return;
    }
    
    public static String accountText(JSONObject object, String link) {
        String page = "";
        page += "<html>\n" +
                "<head>\n" +
                "<style type='text/css'>\n" +
                "	div {\n" +
                "		width: 100%; \n" +
                "		height: 100%; \n" +
                "		overflow: auto;\n" +
                "	}\n" +
                "</style>\n" +
                "<script src=\"https://d3js.org/d3.v4.min.js\"></script>\n" +
                "<script src=\"https://code.jquery.com/jquery-3.2.1.min.js\"></script>\n" +
                "<script>\n" +
                "	link = \"html_posts_0.json.html\";\n" +
                "	var data = ";
        page += object.toJSONString();
        page += "</script>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<table border=\"5\" width=\"100%\" height=\"100%\" style=\"table-layout:fixed;\">\n" +
                "<tr height=\"30em\" align=\"center\">\n" +
                "<td rowspan=6><div id=\"user\" style=\"width: 100%; height: 100%; overflow: auto;\"></div></td>\n" +
                "<td rowspan=2>Постоянные читатели</td>\n" +
                "<td rowspan=2>Избранное</td>\n" +
                "<td rowspan=2>Сообщества</td>\n" +
                "<td colspan=3>Только для сообществ</td>\n" +
                "</tr>\n" +
                "<tr height=\"30em\" align=\"center\">\n" +
                "<td>Владельцы сообщества</td>\n" +
                "<td>Модераторы</td>\n" +
                "<td>Участники сообщества</td>\n" +
                "</tr>\n" +
                "<tr valign=\"top\">\n" +
                "<td><div id=\"readers\"></div></td>\n" +
                "<td><div id=\"favourites\"></div></td>\n" +
                "<td><div id=\"communities\"></div></td>\n" +
                "<td><div id=\"owners\"></div></td>\n" +
                "<td><div id=\"moderators\"></div></td>\n" +
                "<td><div id=\"members\"></div></td>\n" +
                "</tr>\n" +
                "<tr height=\"30em\" align=\"center\">\n" +
                "<td rowspan=2>Любимые теги</td>\n" +
                "<td rowspan=2>Белый список</td>\n" +
                "<td rowspan=2>Черный список</td>\n" +
                "<td colspan=3>Может не использоваться</td>\n" +
                "</tr>\n" +
                "<tr height=\"30em\" align=\"center\">\n" +
                "<td>профиль-список</td>\n" +
                "<td>дневник-список</td>\n" +
                "<td>комментарии-список</td>\n" +
                "</tr>\n" +
                "<tr valign=\"top\">\n" +
                "<td><div id=\"tags\"></div></td>\n" +
                "<td><div id=\"white\"></div></td>\n" +
                "<td><div id=\"black\"></div></td>\n" +
                "<td><div id=\"profile\"></div></td>\n" +
                "<td><div id=\"journal\"></div></td>\n" +
                "<td><div id=\"comment\"></div></td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "\n" +
                "\n" +
                "	\n" +
                "<script>	\n" +
                "	access = {\n" +
                "		\"0\":\"открыто для всех\", \n" +
                "		\"1\":\"только для избранных\", \"11\":\"только для избранных\", \n" +
                "		\"2\":\"закрыта для списка\", \"12\":\"закрыта для списка\", \n" +
                "		\"3\":\"только для списка\", \"13\":\"только для списка\", \n" +
                "		\"4\":\"только для белого списка\", \"14\":\"только для белого списка\", \n" +
                "		\"5\":\"только для постоянных читателей\", \"15\":\"только для постоянных читателей\", \n" +
                "		\"6\":\"только для зарегистрированных пользователей\", \"16\":\"только для зарегистрированных пользователей\", \n" +
                "		\"7\":\"закрыто для всех\", \"17\":\"закрыто для всех\"\n" +
                "	}\n" +
                "	\n" +
                "	journal = {\"0\":\"Без дневника\", \"1\":\"Дневник\", \"2\":\"Сообщество\"}\n" +
                "	\n" +
                "	user.innerHTML += \"Пользователь<br>\" + data[\"username\"] + \"<br>id: \" + data[\"userid\"]\n" +
                "	if(data[\"avatar\"] != \"\") {\n" +
                "		user.innerHTML += \"<br><img src=\\\"\" + data[\"avatar\"] + \"\\\">\"\n" +
                "	}\n" +
                "	user.innerHTML += \"<hr>\" + journal[data[\"journal\"]]\n" +
                "	if(data[\"shortname\"] != \"\") {\n" +
                "		user.innerHTML += \"<br>\"\n" +
                "		if(link != \"\") {\n" +
                "			user.innerHTML += \"<a href=\\\"\"+link+\"\\\">\\\"\" + data[\"journal_title\"] + \"\\\"</a>\"\n" +
                "		} else {\n" +
                "			user.innerHTML += \"\\\"\" + data[\"journal_title\"] + \"\\\"\"\n" +
                "		}\n" +
                "	}\n" +
                "	user.innerHTML += \"<hr>Подпись<br>\\\"\" + data[\"by_line\"] + \"\\\"\"\n" +
                "	user.innerHTML += \"<hr>Временная зона<br>\" + data[\"timezone\"]\n" +
                "	user.innerHTML += \"<hr>Доступ к профилю:<br>\" + access[data[\"profile_access\"]]\n" +
                "	user.innerHTML += \"<hr>Доступ к дневнику:<br>\" + access[data[\"journal_access\"]]\n" +
                "	user.innerHTML += \"<hr>Доступ к к комментированию:<br>\" + access[data[\"comment_access\"]]\n" +
                "	\n" +
                "	data['readers'].forEach(function(item) {\n" +
                "		readers.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['favourites'].forEach(function(item) {\n" +
                "		favourites.innerHTML += item+\"<br>\"\n" +
                "	})		\n" +
                "	data['communities'].forEach(function(item) {\n" +
                "		communities.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['owners'].forEach(function(item) {\n" +
                "		owners.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['moderators'].forEach(function(item) {\n" +
                "		moderators.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['members'].forEach(function(item) {\n" +
                "		members.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	\n" +
                "	data['tags'].forEach(function(item) {\n" +
                "		tags.innerHTML += item+\"<br>\"\n" +
                "	})\n" +
                "	data['white_list'].forEach(function(item) {\n" +
                "		white.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['black_list'].forEach(function(item) {\n" +
                "		black.innerHTML += item+\"<br>\"\n" +
                "	})	\n" +
                "	data['profile_list'].forEach(function(item) {\n" +
                "		profile.innerHTML += item+\"<br>\"\n" +
                "	})\n" +
                "	data['journal_list'].forEach(function(item) {\n" +
                "		journal.innerHTML += item+\"<br>\"\n" +
                "	})\n" +
                "	data['comment_list'].forEach(function(item) {\n" +
                "		comment.innerHTML += item+\"<br>\"\n" +
                "	})\n" +
                "	\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
        
        return page;
    }
    
    public static String pageText(JSONObject object, String link_prev, String link_next, String user) {
        String page = "";
        page += "<html>\n" +
                "<head>\n" +
                "<style type='text/css'>\n" +
                "	.hide {\n" +
                "		display:none;\n" +
                "	}\n" +
                "	.MOREbtn {\n" +
                "		border: 1px solid #688bb0;\n" +
                "		width: 1.7em;  \n" +
                "		height: 1.7em;\n" +
                "		text-align: center;\n" +
                "	}\n" +
                "</style>\n" +
                "<script src=\"https://d3js.org/d3.v4.min.js\"></script>\n" +
                "<script src=\"https://code.jquery.com/jquery-3.2.1.min.js\"></script>\n" +
                "<script>\n" +
                "	var prev = \"";
        page += link_prev;
        page += "\"\n" +
                "	var next = \"";
        page += link_next;
        page += "\"\n" +
                "	var username = \"";
        page += user;
        page += "\"\n" +
                "	var data = ";
        page += object.toJSONString();
        page += "\n" +
                "	function showmore(elem) {\n" +
                "		if(elem.getAttribute(\"value\") == \"+\")\n" +
                "			elem.setAttribute(\"value\", \"-\")\n" +
                "		else\n" +
                "			elem.setAttribute(\"value\", \"+\")\n" +
                "		var next = elem.nextElementSibling\n" +
                "		if(next.classList.contains('hide'))\n" +
                "			next.classList.remove('hide')\n" +
                "		else\n" +
                "			next.classList.add('hide')\n" +
                "		next = next.nextElementSibling\n" +
                "		if(next.classList.contains('hide'))\n" +
                "			next.classList.remove('hide')\n" +
                "		else\n" +
                "			next.classList.add('hide')\n" +
                "	}\n" +
                "</script>\n" +
                "</head>\n" +
                "<body>\n" +
                "<table border=\"5\" width=\"100%\" cellpadding=\"10\">\n" +
                "<tr>\n" +
                "<td width=\"25em\" height=\"25em\" align=\"center\" colspan=2 id=\"user\"></td>\n" +
                "<td valign=\"top\" rowspan=3>\n" +
                "	<div id=\"post\"></div>\n" +
                "	<div id=\"comments\"></div>\n" +
                "</td>\n" +
                "<td valign=\"top\" width=\"300em\" rowspan=3>\n" +
                "	<div id=\"voting\"></div>\n" +
                "	<div id=\"rights\"></div>\n" +
                "	<div id=\"list\"></div>\n" +
                "	<div id=\"music\"></div>\n" +
                "	<div id=\"mood\"></div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td width=\"25em\" height=\"25em\" align=\"center\" id=\"prev_page\"></td>\n" +
                "<td width=\"25em\" height=\"25em\" align=\"center\" id=\"next_page\"></td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td id=\"calendar\" valign=\"top\" align=\"center\" colspan=2></td></tr>\n" +
                "</table>\n" +
                "\n" +
                "	\n" +
                "<script>\n" +
                "	user.innerHTML = \"<a href=\\\"html_account.html\\\">\" + username + \"</a>\"\n" +
                "	prev_page.innerHTML = \"<a href=\\\"\" + prev + \"\\\"><<</a>\"\n" +
                "	next_page.innerHTML = \"<a href=\\\"\" + prev + \"\\\">>></a>\"\n" +
                "	\n" +
                "	access = {\n" +
                "		\"0\":\"открытая запись\", \n" +
                "		\"1\":\"только для избранных\", \"11\":\"только для избранных\", \n" +
                "		\"2\":\"закрыта для списка\", \"12\":\"закрыта для списка\", \n" +
                "		\"3\":\"только для списка\", \"13\":\"только для списка\", \n" +
                "		\"4\":\"только для белого списка\", \"14\":\"только для белого списка\", \n" +
                "		\"5\":\"только для постоянных читателей\", \"15\":\"только для постоянных читателей\", \n" +
                "		\"6\":\"только для зарегистрированных пользователей\", \"16\":\"только для зарегистрированных пользователей\", \n" +
                "		\"7\":\"закрыта для всех\", \"17\":\"закрыта для всех\"\n" +
                "	}\n" +
                "	var pst = {\"message_html\":\"[MORE=Много]Очень много[/MORE] текста для [J]юзера[/J]<br><img src=\\\"http://static.diary.ru/userdir/2/7/3/0/2730972/thumb/71430810.jpg\\\">\"}\n" +
                "	var cmmnts = [{\"author_username\":\"d\", \"message_html\":\"комментарий 1\"}, {\"message_html\":\"комментарий 2\"}]\n" +
                "	\n" +
                "	function clear(elem) {\n" +
                "		while (elem.firstChild) {\n" +
                "			elem.removeChild(elem.firstChild)\n" +
                "		}\n" +
                "	}	\n" +
                "	function replaceAll(str, find, replace) {\n" +
                "		return str.replace(new RegExp(find, 'gm'), replace)\n" +
                "	}\n" +
                "	function openMORE(str) {\n" +
                "		i = str.indexOf(\"\\[MORE=\", 0)\n" +
                "		if(i == -1) return str\n" +
                "		do {\n" +
                "			count = 1\n" +
                "			next_open = str.indexOf(\"\\[\", i+1)\n" +
                "			next_close = str.indexOf(\"\\]\", i+1)\n" +
                "			while(count != 0 && next_close != -1) {\n" +
                "				if(next_open > 0 && next_open < next_close) {\n" +
                "					next_open = str.indexOf(\"\\[\", next_open+1)\n" +
                "					count += 1\n" +
                "				} else {\n" +
                "					count -= 1	\n" +
                "					if(count > 0)\n" +
                "						next_close = str.indexOf(\"\\]\", next_close+1)\n" +
                "				}\n" +
                "			}\n" +
                "			if( next_close != -1) {\n" +
                "				str = str.substring(0, i)+\"<input type=\\\"submit\\\" class=\\\"MOREbtn\\\" value=\\\"+\\\" onClick=\\\"return showmore(this);\\\"><div>\"+str.substring(i+6, next_close)+\"</div><div class=\\\"hide\\\">\"+str.substring(next_close+1)\n" +
                "			}\n" +
                "			i = str.indexOf(\"\\[MORE=\", i+1)\n" +
                "		} while(i >= 0)\n" +
                "		return str\n" +
                "	}\n" +
                "	function editMessage(str, post) {\n" +
                "		if(post)\n" +
                "			str = replaceAll(str, \"\\n\", \"<br>\")\n" +
                "		str = replaceAll(str, \"\\\\[/MORE]\", \"</div>\")\n" +
                "		str = openMORE(str)\n" +
                "		return str\n" +
                "	}\n" +
                "	\n" +
                "	data[\"posts\"].forEach(function(item, i) {	  \n" +
                "		var p = document.createElement('p')\n" +
                "		p.setAttribute('value', i)\n" +
                "		p.onclick = function showdata() {\n" +
                "			clear(post)\n" +
                "			item[\"message_html\"] = editMessage(item[\"message_html\"], true)\n" +
                "			var div = document.createElement('div');\n" +
                "			author_username = username\n" +
                "			if(item[\"author_username\"] != undefined) {\n" +
                "				author_username = item[\"author_username\"]\n" +
                "			}\n" +
                "			div.innerHTML = item[\"postid\"]+\", \" + author_username + \"<hr>\" \n" +
                "				+ item[\"dateline_cdate\"] + \"<hr>\" \n" +
                "				+ item[\"title\"] + \"<hr>\" \n" +
                "				+ item[\"message_html\"] + \"<hr>\"\n" +
                "			post.appendChild(div)\n" +
                "			\n" +
                "			clear(comments)\n" +
                "			item[\"comments\"].forEach(function(comment, j) {	  \n" +
                "				comments.appendChild(document.createElement('hr'))\n" +
                "				comment[\"message_html\"] = editMessage(comment[\"message_html\"], false)\n" +
                "				var div = document.createElement('div')\n" +
                "				div.innerHTML = comment[\"dateline\"] + \"\\t\\t\\t\" + comment[\"author_username\"] + \"<hr>\" + comment[\"message_html\"] \n" +
                "				comments.appendChild(div)\n" +
                "			})\n" +
                "			\n" +
                "			clear(voting)\n" +
                "			if('voting' in item) {\n" +
                "				vot = item['voting']				\n" +
                "				if(vot['question'] != '') {	\n" +
                "					text = \"?:\\t\" + vot['question'] + \"<hr><table>\"\n" +
                "					vot['answers'].forEach(function(ans) {		\n" +
                "						text += \"<tr><td>\" + ans['variant'] + \"</td><td>\" + ans['count'] + \"</td><td>\" + ans['percent'] + \"</td></tr>\"						\n" +
                "					})\n" +
                "					text += \"</table>\"\n" +
                "					voting.innerHTML = \"Голосование<hr>\" + text + \"<hr><hr>\"				\n" +
                "				}\n" +
                "			}\n" +
                "			\n" +
                "			clear(rights)\n" +
                "			rights.innerHTML = \"Права доступа<hr>\"\n" +
                "			if(item['access'].length == 2)\n" +
                "				rights.innerHTML += \"+18<hr>\"\n" +
                "			rights.innerHTML += access[item['access']] + \"<hr><hr>\"\n" +
                "			clear(list)\n" +
                "			if(item['access'] == '2' || item['access'] == '12' || item['access'] == '3' || item['access'] == '13') {\n" +
                "				list.innerHTML = \"Список<hr>\"\n" +
                "				console.log(item['access'])\n" +
                "				item['access_list'].forEach(function(ans) {		\n" +
                "					list.innerHTML += ans + \"<br>\"\n" +
                "					console.log(ans)\n" +
                "				})\n" +
                "				list.innerHTML += \"<hr><hr>\"	\n" +
                "			}\n" +
                "			\n" +
                "			clear(music)\n" +
                "			if('current_music' in item) {\n" +
                "				if(item['current_music'] != '')\n" +
                "					music.innerHTML = \"Музыка<hr>\" + item['current_music'] + \"<hr><hr>\"\n" +
                "			}\n" +
                "			clear(mood)\n" +
                "			if('current_mood' in item) {\n" +
                "				if(item['current_mood'] != '')\n" +
                "					mood.innerHTML = \"Настроение<hr>\" + item['current_mood'] + \"<hr><hr>\"\n" +
                "			}\n" +
                "			\n" +
                "		}\n" +
                "		\n" +
                "		p.innerHTML = item['postid']\n" +
                "		calendar.appendChild(p)\n" +
                "	})\n" +
                "	\n" +
                "	\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
        
        return page;
    }
    
}
