package com.kanedias.dybr.exporter;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.kanedias.dybr.exporter.Constants.DIARY_URI;

public class AccountParser {

    public static Account getAccount(HtmlRetriever h, String login) throws IOException, InterruptedException {
        Account acc = new Account();
        acc.username = login;

        DiaryExporter.logger.info("Account_parser start");
        DiaryExporter.logger.info("ignition");
        int all_right = ignition(h, acc);
        if (all_right != 0 || acc.shortname.equals("")) {
            return acc;
        }
        DiaryExporter.logger.info("profile_list_step");
        profile_list_step(h, acc);
        DiaryExporter.logger.info("journal_list_step");
        journal_list_step(h, acc);
        DiaryExporter.logger.info("comment_list_step");
        comment_list_step(h, acc);
        DiaryExporter.logger.info("white_black_list_step");
        white_black_list_step(h, acc);
        DiaryExporter.logger.info("member_step");
        member_step(h, acc);
        DiaryExporter.logger.info("tags_step");
        tags_step(h, acc);
        DiaryExporter.logger.info("profile_step");
        profile_step(h, acc);
        DiaryExporter.logger.info("geography_step");
        geography_step(h, acc);
        DiaryExporter.logger.info("epigraph_step");
        epigraph_step(h, acc);
        DiaryExporter.logger.info("Account_parser stop");

        return acc;
    }

    private static int ignition(HtmlRetriever webClient, Account acc) throws IOException, InterruptedException {
        String html = webClient.get(DIARY_URI.toString());
        Document doc = Jsoup.parse(html, DIARY_URI.toString());

        Element topMenu = doc.selectFirst("ul.navbar-user");
        Element journalTypeSpan = topMenu.selectFirst("span.i-menu-diary + span.alt");
        Element journalLink = journalTypeSpan.parent();
        Element userProfileLink = topMenu.selectFirst("li.username a");
        DiaryExporter.logger.info("find diary " + journalTypeSpan.ownText());
        switch (journalTypeSpan.ownText()) {
            case "Мой дневник":
                acc.journal = "1";
                URI diaryUrl = URI.create(journalLink.absUrl("href"));
                acc.shortname = StringUtils.substringBefore(diaryUrl.getHost(), ".");
                break;
            case "Мое сообщество":
                acc.journal = "2";
                URI communityUrl = URI.create(journalLink.absUrl("href"));
                acc.shortname = StringUtils.substringBefore(communityUrl.getHost(), ".");
                break;
            case "Завести дневник":
                acc.journal = "0";
                break;
            default:
                DiaryExporter.logger.info("have no access");
                return 1;
        }

        acc.userid = StringUtils.substringAfter(userProfileLink.attr("href"), "?");
        return 0;
    }

    private static void profile_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/member/?access").toString()), DIARY_URI.toString());

        Elements access = doc.getElementsByAttributeValue("name", "access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.profile_access = el.attr("value");
                break;
            }
        }

        Element access_list = doc.getElementById("access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.profile_list = list.split("\n");
        }
    }

    private static void journal_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/diary/?access").toString()), DIARY_URI.toString());

        Elements access = doc.getElementsByAttributeValue("name", "access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.journal_access = el.attr("value");
            }
        }

        access = doc.getElementsByAttributeValue("name", "access_mode2");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.journal_access = "" + (Integer.parseInt(acc.journal_access) + Integer.parseInt(el.attr("value")));
            }
        }

        Element access_list = doc.getElementById("access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.journal_list = list.split("\n");
        }
    }

    private static void comment_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/diary/?commentaccess").toString()), DIARY_URI.toString());

        Elements access = doc.getElementsByAttributeValue("name", "comments_access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.comment_access = el.attr("value");
                break;
            }
        }

        Element access_list = doc.getElementById("comments_access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.comment_list = list.split("\n");
        }
    }

    private static void white_black_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/member/?access").toString()), DIARY_URI.toString());

        Element white_list = doc.getElementById("white_list");
        if (white_list != null && white_list.childNodeSize() > 0) {
            String list = white_list.childNode(0).toString();
            acc.white_list = list.split("\n");
        }

        doc = Jsoup.parse(h.get("https://www.diary.ru/options/diary/?pch"));

        Element black_list = doc.getElementById("members");
        if (black_list != null && black_list.childNodeSize() > 0) {
            String list = black_list.childNode(0).toString();
            acc.black_list = list.split("\n");
        }
    }

    private static void member_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        String html = h.get(DIARY_URI.resolve("/member/?" + acc.userid + "&fullreaderslist&fullfavoriteslist&fullcommunity_membershiplist&fullcommunity_moderatorslist&fullcommunity_masterslist&fullcommunity_memberslist").toString());
        Document doc = Jsoup.parse(html, DIARY_URI.toString());
        URI diaryUrl = URI.create("https://www.diary.ru/");

        Element avatar = doc.selectFirst("p.avatar img");
        if (avatar != null) {
            acc.avatar = diaryUrl.resolve(avatar.attr("src")).toString();
        }

        Elements heads = doc.select("div.page-header");
        for (Element head : heads) {
            String title = head.selectFirst("p.large > b").ownText();
            switch (title) {
                case "Дневник":
                case "Сообщество":
                    acc.journal_title = head.parent().select("h1 > a").text();
                    break;
                case "Участник сообществ":
                    Elements communityLinks = head.parent().select("div.links > a");
                    acc.communities = communityLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
                case "Избранные дневники":
                    Elements favLinks = head.parent().select("div.links > a");
                    acc.favourites = favLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
                case "Постоянные читатели":
                    Elements readerLinks = head.parent().select("div.links > a");
                    acc.readers = readerLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
                case "Участники сообщества":
                    Elements memberLinks = head.parent().select("div.links > a");
                    acc.members = memberLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
                case "Владельцы сообщества":
                    Elements ownerLinks = head.parent().select("div.links > a");
                    acc.owners = ownerLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
                case "Модераторы сообщества":
                    Elements modLinks = head.parent().select("div.links > a");
                    acc.moderators = modLinks.stream().map(Element::text).toArray(String[]::new);
                    break;
            }
        }
    }

    private static void tags_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/diary/?tags").toString()), DIARY_URI.toString());

        Element tag_list = doc.getElementById("textarea");
        if (tag_list != null && tag_list.childNodeSize() > 0) {
            String list = tag_list.childNode(0).toString();
            acc.tags = list.split("\n");
        }
    }

    private static void profile_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/member/?profile").toString()), DIARY_URI.toString());

        Elements by_line = doc.getElementsByAttributeValue("name", "usertitle");
        if (by_line.size() > 0) {
            acc.by_line = by_line.first().attr("value");
        }

        String day = StringUtils.leftPad(doc.select("#day option[selected]").val(), 2, "0");
        String month = StringUtils.leftPad(doc.select("#month option[selected]").val(), 2, "0");
        String year = StringUtils.leftPad(doc.select("#year option[selected]").text(), 4, "0");
        acc.birthday = year + "-" + month + "-" + day;


        Element sex = doc.getElementById("malesex");
        if (sex.hasAttr("checked")) {
            acc.sex = "Мужской";
        }
        sex = doc.getElementById("femalesex");
        if (sex.hasAttr("checked")) {
            acc.sex = "Женский";
        }

        List<Node> edu = doc.getElementById("education").childNodes();
        for (Node n : edu) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.education = n.childNode(0).toString();
                }
            }
        }

        List<Node> sfera = doc.getElementById("sfera").childNodes();
        for (Node n : sfera) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.sfera = n.childNode(0).toString();
                }
            }
        }

        Element about = doc.getElementById("about");
        if (about != null && about.childNodeSize() > 0) {
            acc.about = about.childNode(0).toString();
        }
    }

    private static void geography_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/member/?geography").toString()), DIARY_URI.toString());

        List<Node> countries = doc.getElementsByAttributeValue("name", "country").first().childNodes();
        for (Node n : countries) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.country = n.childNode(0).toString();
                }
            }
        }

        List<Node> cities = doc.getElementsByAttributeValue("name", "city").first().childNodes();
        for (Node n : cities) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.city = n.childNode(0).toString();
                }
            }
        }
        acc.city += doc.getElementsByAttributeValue("name", "other").first().attr("value");

        Elements timezone = doc.getElementsByAttributeValue("name", "timezoneoffset").first().getElementsByTag("option");
        for (Element el : timezone) {
            if (el.hasAttr("selected")) {
                acc.timezone = el.attr("value");
            }
        }
    }

    private static void epigraph_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get(DIARY_URI.resolve("/options/diary/?owner").toString()), DIARY_URI.toString());
        Element ep = doc.getElementById("message");
        if (ep != null && ep.childNodeSize() > 0) {
            acc.epigraph = ep.childNode(0).toString();
        }
    }
}
