import json
import requests
import scrape
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
        self.__use_white_list = False
    def get_sid(self, login, password):
        self.__time = datetime.min
        self.__login = login
        self.__password = password
        m = md5()
        m.update((self.__api_pass.encode('windows-1251')+password.encode('windows-1251')))
        data = {'method': 'user.auth', 'username': login.encode('windows-1251'), 'password': m.hexdigest(), 'appkey': self.__api_key}
        req = self.__session.post('http://www.diary.ru/api/', data=data)
        resp = json.loads(req.content.decode())
        self.account = {}
        if resp['result'] == '0':
            self.__time = datetime.now()
            self.__sid = resp['sid']
        else:
            self.__time = datetime.min
            self.__sid = ''
            self.__login = ''
            self.__password = ''
            # print('\nwrong login or password')
        return self.__sid
    def __reauth(self):
        delta = datetime.now() - self.__time
        if self.__login and self.__password and (delta.days or delta.seconds > 1198):
            self.get__sid(self.__login, self.__password)
        # if not self.__sid:
        #     print('you don\'t auth')
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

        fields = ['author_username', 'author_title', 'tags_data', 'access', 'jaccess', 'message_src',
                  'no_comments', 'comments_count_data', 'postid',
                  'access_list', 'poll_title', 'poll_multiselect', 'poll_end',
                  'dateline_date', 'dateline_cdate']
        for i in range(1, 11):
            fields.append('poll_answer_'+str(i))
        data={'sid':self.__sid, 'method':'post.get', 'type':'diary', 'from':'0', 'src':1,  'fields': ','.join(fields)}
        posts = []
        self.account['votings'] = []
        ind = 0
        while 1:
            data['from'] = str(ind)
            req = self.__session.post('http://www.diary.ru/api/', data=data)
            dict = json.loads(req.content.decode())['posts']
            for h in dict:
                post = {}
                self.account['access'] = dict[h].pop('jaccess')
                for hh in dict[h]:
                    post[hh] = dict[h][hh]
                if self.account['journal'] == '1':
                    self.account['by-line'] = post.pop('author_title', '')
                    post.pop('author_username', '')
                elif post['author_username'] == self.account['username']:
                    self.account['by-line'] = post.pop('author_title', '')
                else: post.pop('author_title', '')
                if post['access'] == '4':
                    self.__use_white_list = True
                post['tags'] = list(post.pop('tags_data', {}).values())
                if 'poll_title' in post:
                    self.account['votings'].append(post['postid'])
                    post['voting'] = {
                        'question': post.pop('poll_title', ''),
                        'end': post.pop('poll_end') != '0',
                        'multiselect': post.pop('poll_multiselect') != '0',
                        'answers':[]
                    }
                post['comments'] = []
                self.__get_comments(post)
                posts.append(post)
            if len(dict) < 20: break
            ind += 20
        self.account['posts'] = posts
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
            for comment in comments:
                post['comments'].append({h: comments[comment].get(h, '') for h in fields})

        return self.account
    def __get_tags(self):
        if not self.__reauth(): return
        tags_names = []
        if self.account.get('tags', ''):
            tags = self.account['tags'].split(',')
            for i in range(0, len(tags), 100):
                data = {'sid': self.__sid, 'method': 'tags.get'}
                for j in range(i, min(i+100, len(tags))):
                    data['tagid['+str(j)+']'] = tags[j]
                req = self.__session.post('http://www.diary.ru/api/', data = data)
                tags_names += list(json.loads(req.content.decode())['tags'].values())
        self.account['tags'] = tags_names
        return self.account
    def __get_info_with_parser(self):
        class Parser(HTMLParser):
            elem = ''
            info = {'answers': [], 'list':[], 'white_list':[], 'black_list':[], 'tags':[]}
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
                    self.info['link'] = next(filter(lambda x: x[0] == 'href', attrib), ('', ''))[1]
                    self.elem == 'exit'
                elif tag == 'input':
                    name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
                    if name == 'usertitle':
                        self.info['title'] = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
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

            def handle_endtag(self, tag):
                if self.elem == 'exit': return

                if tag == 'tr':
                    if all([x in self.answer for x in ['variant', 'count', 'percent']]):
                        self.info['answers'].append(self.answer)
                    self.answer = {}
                elif tag == 'table' and 'voting' in self.elem:
                    self.elem = 'exit'
            def handle_data(self, data):
                if self.elem == 'exit': return

                data = data.lstrip().rstrip()
                if not data: return

                if self.elem == 'visible voting':
                    if 'variant' not in self.answer:
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

        parser = Parser()

        data = {'user_login': self.__login, 'user_pass': self.__password}
        self.__session.post('http://www.diary.ru/', data=data)

        if self.account['journal'] != '0':
            for post in self.account['posts']:
                if 'voting' in post:
                    r = self.__session.post('http://iossh.diary.ru/p'+post['postid']+'.htm')
                    parser.feed(r.text)
                    if 'link' in parser.info:
                        parser.elem = ''
                        r = self.__session.post(parser.info['link'])
                        parser.feed(r.text)
                    post['voting']['answers'] = parser.info['answers']

        r = self.__session.post('http://www.diary.ru/options/member/?profile')
        parser.feed(r.text)
        self.account['by-line'] = parser.info.get('title', '')

        parser.elem = ''
        r = self.__session.post('http://www.diary.ru/options/member/?access')
        parser.feed(r.text)
        self.account['profile_list'] = parser.info['list']

        parser.elem = ''
        parser.info['list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?access')
        parser.feed(r.text)
        self.account['journal_list'] = parser.info['list']

        parser.elem = ''
        parser.info['list'] = []
        parser.info['white_list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?commentaccess')
        parser.feed(r.text)
        self.account['comment_list'] = parser.info['list']
        self.account['white_list'] = parser.info['white_list']

        parser.elem = ''
        parser.info['black_list'] = []
        r = self.__session.post('http://www.diary.ru/options/diary/?pch')
        parser.feed(r.text)
        self.account['black_list'] = parser.info['black_list']

        parser.elem = ''
        r = self.__session.post('http://www.diary.ru/options/diary/?tags')
        parser.feed(r.text)
        self.account['tags'] = [tag.lstrip().rstrip() for tag in parser.info['tags']]

        return self.account

    def get_all_info(self):
        while not self.__sid:
            # print('you don\'t auth')
            # print('call \"get_sid\" with your login and password before trying to get data')
            return
        self.__get_account_info()
        self.__get_posts()
        self.__get_tags()
        self.__get_info_with_parser()
        return self.account

try:
    print('КОМАНДА ДЫБРА ПРИВЕТСТВУЕТ ВАС!')
    print('\nПожалуйства, введите данные своей учетной записи.')
    print('Ваши логин и пароль необходимы для выгрузки дневника и не будут записаны где-либо.')
    print('логин: ', end='', flush=True)
    login = input()
    print('пароль: ', end='', flush=True)
    pas = input()

    begin = datetime.now()
    di = diary_integrator()
    while 1:
        rez = di.get_sid(login, pas)
        if not rez:
            print('\nВведенные данные неверны.')
            print('Попробуйте снова.')
            print('логин: ', end='', flush=True)
            login = input()
            print('пароль: ', end='', flush=True)
            pas = input()
            rez = di.get_sid(login, pas)
        else:
            break
    print('\nНачинаем выгрузку...')
    rez = di.get_all_info()

    di.account['username']
    json.dump(di.account, open('diary_'+login.replace(' ', '_')+'.json', 'w', encoding="utf-16"), ensure_ascii=False
              , indent=4
              )
    print('\nВыгрузка записей и комментариев произведена успешно.')
    print('Данные сохранены в файл diary_'+login.replace(' ', '_')+'.json')

    print('\nСтатистика')
    if di.account['journal'] != '0':
        print('Количество записей:\t', len(di.account['posts']))
    print('Время выгрузки:\t', datetime.now() - begin)
except Exception as exc:
    file = open('error_log.txt', 'w')
    file.write(str(exc))
    file.close()
    print('В процессе выгрузки случилось что-то не то. Пожалуйста, сообщите об этом в сообщество вконтакте vk.com/aboutdybr')
    print('Данные об ошибке выгружены в файл error_log.txt')

input()











