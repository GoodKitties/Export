import requests
from html.parser import HTMLParser
from PyQt5.QtCore import pyqtSignal


class accessParser(HTMLParser):
    obj = ''
    info = {'access':'0', 'list': [], 'white_list': [], 'black_list': []}
    answer = {}

    def handle_starttag(self, tag, attrib):
        if tag == 'input':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            checked = next(filter(lambda x: x[0] == 'checked', attrib), None)
            if 'access_mode' in name and 'album' not in name and checked:
                value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
                self.info['access'] = str(int(self.info['access']) + int(value))
        elif tag == 'textarea':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            if name in ['access_list', 'comments_access_list']:
                self.obj = 'access'
            elif name == 'white_list':
                self.obj = 'white'
            elif name == 'members':
                self.obj = 'black'

    def handle_endtag(self, tag):
        pass

    def handle_data(self, data):
        data = data.lstrip().rstrip()
        if not data: return

        if self.obj == 'access':
            data = data.lstrip().split('\n')
            if data:
                if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                    data = []
                if '' in data:
                    data.remove('')
            self.info['list'] = data
            self.obj = ''
        elif self.obj == 'white':
            data = data.split('\n')
            if data:
                if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                    data = []
                if '' in data:
                    data.remove('')
            self.info['white_list'] = data
            self.obj = ''
        elif self.obj == 'black':
            data = data.split('\n')
            if data:
                if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                    data = []
                if '' in data:
                    data.remove('')
            self.info['black_list'] = data
            self.obj = ''
        elif self.obj == 'tags':
            data = data.split('\n')
            if data:
                if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                    data = []
                if '' in data:
                    data.remove('')
            self.info['tags'] = data
            self.obj = ''


accessparser = accessParser()

def get_access_lists(session, message):
    rezult = {}

    accessparser.obj = ''
    accessparser.info['access'] = '0'
    r = session.post('http://www.diary.ru/options/member/?access')
    accessparser.feed(r.text)
    rezult['profile_list'] = accessparser.info['list']
    rezult['profile_access'] = accessparser.info['access']

    accessparser.obj = ''
    accessparser.info['list'] = []
    accessparser.info['access'] = '0'
    r = session.post('http://www.diary.ru/options/diary/?access')
    accessparser.feed(r.text)
    rezult['journal_list'] = accessparser.info['list']
    rezult['journal_access'] = accessparser.info['access']

    accessparser.obj = ''
    accessparser.info['list'] = []
    accessparser.info['access'] = '0'
    accessparser.info['white_list'] = []
    r = session.post('http://www.diary.ru/options/diary/?commentaccess')
    accessparser.feed(r.text)
    rezult['comment_list'] = accessparser.info['list']
    rezult['comment_access'] = accessparser.info['access']
    rezult['white_list'] = accessparser.info['white_list']

    accessparser.obj = ''
    accessparser.info['black_list'] = []
    r = session.post('http://www.diary.ru/options/diary/?pch')
    accessparser.feed(r.text)
    rezult['black_list'] = accessparser.info['black_list']
    message.emit('Списки доступа получены')

    return rezult