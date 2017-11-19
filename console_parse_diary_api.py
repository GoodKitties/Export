import json
import requests
import scrape
import re
import sys
from hashlib import md5
from datetime import datetime
from html.parser import HTMLParser

class diary_integrator:
    def __init__(self):
        self.__session = scrape.create_scraper()
        self.__api_pass = 'ЗДЕСЬ ДОЛЖЕН БЫТЬ ПАРОЛЬ АПИ'
        self.__api_key = 'ЗДЕСЬ ДОЛЖЕН БЫТЬ КЛЮЧ АПИ'
        self.__sid = ''
        self.__time = datetime.min
        self.__login = ''
        self.__password = ''
        self.account = {}
        self.posts = []
    def get_sid(self, login, password):
        self.__time = datetime.min
        self.__login = login.encode('windows-1251')
        self.__password = password.encode('windows-1251')
        m = md5()
        m.update((self.__api_pass.encode('windows-1251')+password.encode('windows-1251')))
        data = {'method': 'user.auth', 'username': login.encode('windows-1251'), 'password': m.hexdigest(), 'appkey': self.__api_key}
        req = self.__session.post('http://www.diary.ru/api/', data=data)
        resp = json.loads(req.content.decode())
        if resp['result'] == '0':
            self.__time = datetime.now()
            self.__sid = resp['sid']
        else:
            self.__time = datetime.min
            self.__sid = ''
            self.__login = ''
            self.__password = ''
        return self.__sid
    def __reauth(self):
        delta = datetime.now() - self.__time
        if self.__login and self.__password and (delta.days or delta.seconds > 1198):
            self.get__sid(self.__login, self.__password)
        return self.__sid
    def __get_account_info(self):
        if not self.__reauth(): return
        fields = ['username', 'shortname', 'journal', 'journal_title', 'avatar', 'userid',
                  'interest', 'about', 'email', 'icq',
                  'country', 'region', 'city',
                  'sex', 'education', 'sfera', 'birthday',
                  'favs2', 'readers2', 'community.member2',
                  'mycommunity.members2', 'mycommunity.masters2', 'mycommunity.moderators2']
        data = {'sid': self.__sid, 'method': 'user.get', 'fields': ','.join(fields)}
        req = self.__session.post('http://www.diary.ru/api/', data=data)

        dict = json.loads(req.content.decode())['user']
        for h in dict:
            self.account[h] = dict[h]
        for h_pair in [('favs2', 'favourites'), ('readers2', 'readers'), ('community.member2', 'communities'),
                       ('mycommunity.members2', 'members'), ('mycommunity.masters2', 'owners'), ('mycommunity.moderators2', 'moderators')]:
            self.account[h_pair[1]] = list(self.account.pop(h_pair[0], {}).values())
        if 'avatar' in self.account:
            self.account['avatar'] = 'http://www.diary.ru'+self.account['avatar']
        self.account['by-line'] = ''
        return self.account
    def __get_posts(self):
        if not self.__reauth(): return
        if self.account['journal'] == '0': return self.account

        fields = ['author_username', 'author_title', 'tags_data', 'access', 'message_src',
                  'no_comments', 'comments_count_data', 'postid',
                  'current_music', 'current_mood', 'title',
                  'access_list', 'poll_title', 'poll_multiselect', 'poll_end',
                  'dateline_date', 'dateline_cdate']
        data={'sid':self.__sid, 'method':'post.get', 'type':'diary', 'src':1, 'fields': ','.join(fields)}
        add_data={'sid':self.__sid, 'method':'post.get', 'type':'diary', 'src':0, 'fields': 'message_html'}
        ind = 0
        while 1:
            data['from'] = str(ind)
            req = self.__session.post('http://www.diary.ru/api/', data=data)
            dict = json.loads(req.content.decode())['posts']
            if self.account['journal'] == '2':
                add_data['from'] = str(ind)
                add_req = self.__session.post('http://www.diary.ru/api/', data=add_data)
                add_dict = json.loads(add_req.content.decode())['posts']
            for h in dict:
                post = {}
                for hh in dict[h]:
                    post[hh] = dict[h][hh]
                if self.account['journal'] == '2':
                    post['message_html'] = add_dict[h]['message_html']
                if self.account['journal'] == '1':
                    self.account['by-line'] = post.pop('author_title', '')
                    post['message_html'] = post.pop('message_src')
                    post.pop('author_username', '')
                elif post['author_username'] == self.account['username']:
                    self.account['by-line'] = post.pop('author_title', '')
                else: post.pop('author_title', '')
                if 'access_list' in post:
                    post['access_list'] = post['access_list'].split('\n')
                post['tags'] = list(post.pop('tags_data', {}).values())
                if 'poll_title' in post:
                    post['voting'] = {
                        'question': post.pop('poll_title', ''),
                        'end': post.pop('poll_end') != '0',
                        'multiselect': post.pop('poll_multiselect') != '0',
                        'answers':[]
                    }
                self.__parse_html_message(post)
                post['comments'] = []
                self.__get_comments(post)
                self.posts.append(post)
            if len(dict) < 20: break
            ind += 20
            print('GOT POSTS:', ind)
        return self.account
    def __get_comments(self, post):
        if not self.__reauth(): return

        count = int(post.pop('comments_count_data', '0'))
        if count:
            fields = ['author_username', 'message_html', 'dateline']
            # если использовать fields в запросе, то выгружается только последний комментарий
            data = {'sid': self.__sid, 'method': 'comment.get', 'postid': post['postid']}
            req = self.__session.post('http://www.diary.ru/api/', data=data)
            comments = json.loads(req.content.decode())['comments']
            for c in comments:
                comment = {h: comments[c].get(h, '') for h in fields}
                self.__parse_html_message(comment)
                post['comments'].append(comment)

        return self.account
    def __get_info_with_parser(self):
        class Parser(HTMLParser):
            elem = ''
            info = {'answers': [], 'list':[], 'white_list':[], 'black_list':[], 'tags':[], 'access':'0', 'timezone':'0', 'epigraph':''}
            answer = {}

            def handle_starttag(self, tag, attrib):
                if self.elem == 'exit': return

                if tag == 'div':
                    cl = next(filter(lambda x: x[0] == 'class', attrib), ('', ''))[1]
                    if 'voting' in cl.split():
                        self.elem = 'voting'
                elif tag == 'table' and self.elem == 'voting':
                    self.elem = 'visible voting'
                elif tag == 'a' and self.elem == 'voting':
                    link = next(filter(lambda x: x[0] == 'href', attrib), ('', ''))[1]
                    if 'diary.ru' not in link:
                        link = 'http://www.diary.ru' + link
                    self.info['link'] = link
                    self.elem = 'exit'
                elif tag == 'input':
                    name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
                    checked = next(filter(lambda x: x[0] == 'checked', attrib), None)
                    if name == 'usertitle':
                        self.info['title'] = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
                    elif 'access_mode' in name and 'album' not in name and checked:
                        value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
                        self.info['access'] = str(int(self.info['access']) + int(value))
                elif tag == 'textarea':
                    name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
                    if name in ['access_list', 'comments_access_list']:
                        self.elem = 'access'
                    elif name == 'white_list':
                        self.elem = 'white'
                    elif name == 'members':
                        self.elem = 'black'
                    elif name == 'fav_tags':
                        self.elem = 'tags'
                elif tag == 'select':
                    name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
                    if name == 'timezoneoffset':
                        self.elem = 'timezone'
                elif tag == 'option' and self.elem == 'timezone':
                    if next(filter(lambda x: x[0] == 'selected', attrib), None):
                        value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
                        self.info['timezone'] = value

            def handle_endtag(self, tag):
                if self.elem == 'exit': return

                if tag == 'tr':
                    if all([x in self.answer for x in ['variant', 'count', 'percent']]):
                        self.info['answers'].append(self.answer)
                    self.answer = {}
                elif tag == 'table' and 'voting' in self.elem:
                    self.elem = 'exit'
                elif tag == 'select' and self.elem == 'timezone':
                    self.elem = ''

            def handle_data(self, data):
                if self.elem == 'exit': return

                data = data.lstrip().rstrip()
                if not data: return

                if self.elem == 'visible voting':
                    if 'variant' not in self.answer:
                        if data[:5].lower() == 'всего':
                            self.elem = 'exit'
                            return
                        self.answer['variant'] = data[3:].lstrip().rstrip()
                    elif 'count' not in self.answer:
                        self.answer['count'] = data
                    elif 'percent' not in self.answer:
                        self.answer['percent'] = data
                elif self.elem == 'access':
                    data = data.lstrip().split('\n')
                    if data:
                        if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                            data = []
                        if '' in data:
                            data.remove('')
                    self.info['list'] = data
                    self.elem = ''
                elif self.elem == 'white':
                    data = data.split('\n')
                    if data:
                        if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                            data = []
                        if '' in data:
                            data.remove('')
                    self.info['white_list'] = data
                    self.elem = ''
                elif self.elem == 'black':
                    data = data.split('\n')
                    if data:
                        if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                            data = []
                        if '' in data:
                            data.remove('')
                    self.info['black_list'] = data
                    self.elem = ''
                elif self.elem == 'tags':
                    data = data.split('\n')
                    if data:
                        if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                            data = []
                        if '' in data:
                            data.remove('')
                    self.info['tags'] = data
                    self.elem = ''
                elif self.elem == 'epigraph':
                    self.info['epigraph'] = data
                    self.elem = ''

        parser = Parser()

        data = {'user_login': self.__login, 'user_pass': self.__password}
        self.__session.post('http://www.diary.ru/', data=data)

        if self.account['journal'] != '0':
            for post in self.posts:
                if 'voting' in post:
                    r = self.__session.post('http://diary.ru/~'+self.account['shortname']+'/p'+post['postid']+'.htm')
                    parser.feed(r.text)
                    if 'link' in parser.info:
                        parser.elem = 'voting'
                        r = self.__session.post(parser.info['link'])
                        parser.feed(r.text)
                    post['voting']['answers'] = parser.info['answers']

        r = self.__session.post('http://www.diary.ru/options/member/?profile')
        parser.feed(r.text)
        self.account['by-line'] = parser.info.get('title', '')

        parser.elem = ''
        parser.info['access'] = '0'
        parser.info['list'] = []
        r = self.__session.post('http://www.diary.ru/options/member/?access')
        parser.feed(r.text)
        self.account['profile_list'] = parser.info['list']
        self.account['profile_access'] = parser.info['access']

        parser.elem = ''
        parser.info['access'] = '0'
        parser.info['list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?access')
        parser.feed(r.text)
        self.account['journal_list'] = parser.info['list']
        self.account['journal_access'] = parser.info['access']

        parser.elem = ''
        parser.info['access'] = '0'
        parser.info['list'] = []
        parser.info['white_list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?commentaccess')
        parser.feed(r.text)
        self.account['comment_list'] = parser.info['list']
        self.account['comment_access'] = parser.info['access']
        self.account['white_list'] = parser.info['white_list']

        parser.elem = ''
        parser.info['black_list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?pch')
        parser.feed(r.text)
        self.account['black_list'] = parser.info['black_list']

        parser.elem = ''
        r = self.__session.post('http://www.diary.ru/options/diary/?tags')
        parser.feed(r.text)
        if 'Выводить список любимых тем в форме новой записи' in parser.info['tags']:
            parser.info['tags'].remove('Выводить список любимых тем в форме новой записи')
        self.account['tags'] = [tag.lstrip().rstrip() for tag in parser.info['tags']]

        parser.elem = ''
        r = self.__session.post('http://www.diary.ru/options/member/?geography')
        parser.feed(r.text)
        self.account['timezone'] = parser.info['timezone']

        parser.elem = ''
        parser.info['epigraph'] = ''
        r = self.__session.post('http://www.diary.ru/options/diary/?owner')
        parser.feed(r.text)
        self.account['epigraph'] = parser.info['epigraph']

        return self.account

    def __parse_html_message(self, element):
        if 'message_html' not in element: return

        diaryname = '0-9a-zA-Zа-яА-Я\-\_\~\!\@\#\$\&\*\(\)\+\?\=\/\|\\\.\,\;\:\<\>\[\] '
        diarylink = '0-9a-zA-Z\-\_\.\!\~\*\'\\(\)\/\:'
        fullJ = r'(?:<a class="TagJIco" href="(?:http://www.diary.ru|)/member/\?[0-9]+" title="профиль" target=(?:\'|\")?_blank(?:\'|\")?>(?:&nbsp;|)</a>|)<a class="TagL" href="[' + diarylink + ']+.diary.ru" title="(?:дневник: |)[' + diaryname + ']+" target=(?:\'|\")?_blank(?:\'|\")?>([' + diaryname + ']+)</a>'
        openMORE = r'(<a href=(?:\'|\")(?:/~[' + diarylink + ']+/p[0-9]+.htm\?oam|)#more[0-9]*(?:\'|\") class=(?:\'|\")?LinkMore(?:\'|\")? on(?:c|C)lick=(?:\'|\")var e=event; if \(swapMore\(\"c?[0-9]+m[0-9]+\", e.ctrlKey \|\| e.altKey\)\) document.location = this.href; return false;(?:\'|\") id=(?:\'|\")?linkmorec?[0-9]+m[0-9]+(?:\'|\")? ?>)'
        startMORE = r"(</a><span id=morec?[0-9]+m[0-9]+ ondblclick='return swapMore\(\"c?[0-9]+m[0-9]+\"\);' style='display:none;visibility:hidden;'><a name='morec?[0-9]+m[0-9]+start'></a>)"
        closeMORE = r"<a name='morec?[0-9]+m[0-9]+end'></a></span>"
        movePOST = r"(<br><div><a href='http://[" + diarylink + "]+.diary.ru/p[0-9]+.htm\?down&signature=[0-9a-zA-Z]+' title='вернуть на место' onclick='return confirm\(\"Вы уверены, что хотите вернуть запись на место\?\"\);'><small>запись создана: [0-9.]+ в [0-9:]+</small></a></div>$)"
        closePOST = r'\[close_text\][^\[]*\[\/close_text\]'
        spanIMG = r'<span>(<img[^<]+)</span>'
        onloadSMILE = r' onload=\"setSImg\(this\);\"'

        body = element.pop('message_html', '')
        body = re.sub(fullJ, r'[J]\1[/J]', body)
        body = re.sub(openMORE, '[MORE=', body)
        body = re.sub(startMORE, ']', body)
        body = re.sub(closeMORE, '[/MORE]', body)
        body = re.sub(movePOST, '', body)
        body = re.sub(closePOST, '', body)
        body = re.sub(spanIMG, r'\1', body)
        body = re.sub(onloadSMILE, '', body)
        element['message_html'] = body

    def get_all_info(self):
        while not self.__sid:
            return
        self.__get_account_info()
        self.__get_posts()
        self.__get_info_with_parser()
        print('DONE')
        return self.account

if len(sys.argv) >= 3:
    login = sys.argv[1]
    pas = sys.argv[2]
else:
    login = input()
    pas = input()

di = diary_integrator()
rez = di.get_sid(login, pas)
if not rez:
    print('error - auth failed')
    exit()
rez = di.get_all_info()
print(di.account)
for post in di.posts:
    print(post)
print('END')

