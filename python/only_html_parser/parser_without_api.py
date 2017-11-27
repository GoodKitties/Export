import scrape
import parsers.get_access_lists
import parsers.get_links
import parsers.get_diary
import parsers.get_info
import parsers.get_member_info
import json
import os
import sys
import requests
from hashlib import md5
from datetime import datetime
from PyQt5.QtCore import QThread, QObject, pyqtSignal, pyqtSlot

class Exporter(QObject):
    session = None
    finished = pyqtSignal()
    message = pyqtSignal(str)
    login = ''
    pas = ''
    path = ''

    def create_connection(self):
        data = {'user_login': self.login.encode('windows-1251'), 'user_pass': self.pas.encode('windows-1251')}

        self.session = scrape.CloudflareScraper().create_scraper()
        r = self.session.post('http://www.diary.ru/', data=data)
        if 'дневники' in r.text.lower():
            print('diary')
            print(r)

    def add_hash(self, data, name):
        m = md5()
        dataSTR = str(data)+name
        m.update(dataSTR.encode('utf-8'))
        data['hash'] = m.hexdigest()

    def generate_json(self):
        try:
            dir = self.path
            begin = datetime.now()
            rezult = parsers.get_links.get_diary(self.session, self.message)
            if not rezult:
                self.message.emit('Неверные логин/пароль.')
                return
            self.message.emit('Подключение успешно.')
            rezult.update(parsers.get_access_lists.get_access_lists(self.session, self.message))
            rezult.update(parsers.get_info.get_info(self.session, self.message))
            rezult.update(parsers.get_member_info.get_info(self.session, rezult['userid']))

            posts = parsers.get_links.get_posts_links(self.session, self.message)
            posts.reverse()

            dir += '/diary_'+rezult['shortname']
            if not os.path.exists(dir):
                os.makedirs(dir)

            self.add_hash(rezult, rezult['shortname'])
            json.dump(rezult, open(dir+'/account.json', 'w', encoding="utf-8"), ensure_ascii=False
                      , indent=4
                      )
            if posts:
                zlen = len(str(int((len(posts) - 1) / 20)))
                n = 0
                i = 0
                while n < len(posts):
                    rez = {'posts': parsers.get_diary.get_posts(self.session, 'http://'+rezult['shortname']+'.diary.ru', posts[n:n+20], len(posts), n, self.message)}
                    self.add_hash(rez, rezult['shortname'])
                    json.dump(rez, open(dir+'/posts_'+str(i).zfill(zlen)+'.json', 'w', encoding="utf-8")
                              , ensure_ascii=False
                              , indent=4
                              )
                    n += 20
                    i += 1

            self.message.emit('Выгрузка произведена успешно.'+
                              '\nДанные сохранены в папку\n'+dir+
                              '\nВремя выгрузки:\t'+str(datetime.now() - begin))
        except Exception as exc:
            file = open(dir+'/error_log.txt', 'w')
            file.write(str(exc))
            file.close()
            self.message.emit('Cлучилось что-то не то.'
                              '\nПожалуйста, сообщите об этом в сообщество вконтакте vk.com/aboutdybr'
                              '\nДанные об ошибке выгружены в файл error_log.txt')

    def __init__(self, login, pas, path, parent=None):
        QThread.__init__(self, parent)
        self.login = login
        self.pas = pas
        self.path = path
        print('change')
        if self.session:
            self.session.close()

    def make_all(self):
        self.create_connection()
        self.generate_json()
        self.finished.emit()
