import requests
from html.parser import HTMLParser


class infoParser(HTMLParser):
    obj = ''
    info = {'tags': [], 'timezone': '0', 'day':'', 'month':'', 'year':'', 'city':'', 'epigraph':''}
    answer = {}

    def handle_starttag(self, tag, attrib):
        if tag == 'input':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
            checked = next(filter(lambda x: x[0] == 'checked', attrib), None)
            if name == 'usertitle':
                self.info['title'] = value
            if name == 'title':
                self.info['journal-title'] = value
            elif name == 'sex' and checked:
                self.info['sex'] = value
            elif name == 'other' and not self.info['city']:
                self.info['city'] = value
        elif tag == 'textarea':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            if name == 'fav_tags':
                self.obj = 'tags'
            elif name == 'about':
                self.obj = 'about'
            elif name == 'message':
                self.obj = 'epigraph'
        elif tag == 'select':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            if name == 'timezoneoffset':
                self.obj = 'timezone'
            elif name in ['month', 'day', 'year', 'education', 'sfera']:
                self.obj = name
            elif name in ['country', 'city']:
                self.obj = 'from_'+name
        elif tag == 'option':
            selected = next(filter(lambda x: x[0] == 'selected', attrib), None)
            if selected:
                value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
                if self.obj == 'timezone':
                    self.info['timezone'] = value
                elif self.obj in ['month', 'day', 'education', 'sfera']:
                    self.info[self.obj] = value
                elif self.obj == 'year':
                    self.obj = 'byear'
                    return
                elif self.obj in ['from_country', 'from_city']:
                    self.obj = self.obj[5:]
                    return
                self.obj = ''


    def handle_endtag(self, tag):
        if tag == 'select' and self.obj in ['timezone', 'city']:
            self.obj = ''

    def handle_data(self, data):
        data = data.lstrip().rstrip()

        if self.obj == 'tags':
            data = data.split('\n')
            if data:
                if 'get(\'' in data[0] and '\').onfocus()' in data[0]:
                    data = []
                if '' in data:
                    data.remove('')
            self.info['tags'] = data
            self.obj = ''
        elif self.obj == 'byear':
            self.info['year'] = data
            self.obj = ''
        elif self.obj in ['about', 'epigraph']:
            self.info[self.obj] = data
            self.obj = ''
        elif self.obj in ['city', 'country']:
            self.info[self.obj] = data
            self.obj = ''


infoparser = infoParser()

def get_info(session, label):
    rezult = {}

    r = session.post('http://www.diary.ru/options/member/?profile')
    infoparser.feed(r.text)
    rezult['by-line'] = infoparser.info.get('title', '')
    rezult['birthday'] = '-'.join([str(infoparser.info.get(h, '')).zfill(2) for h in ['year', 'month', 'day']])
    rezult['sex'] = {'1':'Мужской', '2':'Женский'}[infoparser.info['sex']]
    rezult['education'] = {'0':'',
                           '1':'Необразован',
                           '2':'Начальное',
                           '3':'Среднее',
                           '4':'Специальное',
                           '5':'Неоконченное высшее',
                           '6':'Высшее',
                           '7':'Кандидат наук',
                           '8':'Доктор наук'}[infoparser.info['education']]
    rezult['sfera'] = {'0':'',
                           '1':'Руководитель',
                           '2':'Специалист',
                           '3':'Служащий',
                           '4':'Рабочий',
                           '5':'Учащийся',
                           '6':'Домохозяйка',
                           '7':'Другое (не работающий)'}[infoparser.info['sfera']]
    rezult['about'] = infoparser.info.get('about', '')

    infoparser.obj = ''
    r = session.post('http://www.diary.ru/options/diary/?tags')
    infoparser.feed(r.text)
    if 'Выводить список любимых тем в форме новой записи' in infoparser.info['tags']:
        infoparser.info['tags'].remove('Выводить список любимых тем в форме новой записи')
    rezult['tags'] = [tag.lstrip().rstrip() for tag in infoparser.info['tags']]
    print('Любимые теги получены')

    infoparser.obj = ''
    infoparser.info['city'] = ''
    r = session.post('http://www.diary.ru/options/member/?geography')
    infoparser.feed(r.text)
    rezult['timezone'] = infoparser.info['timezone']
    rezult['country'] = infoparser.info['country']
    rezult['city'] = infoparser.info['city']

    infoparser.obj = ''
    r = session.post('http://www.diary.ru/options/diary/?title')
    infoparser.feed(r.text)
    rezult['journal_title'] = infoparser.info['journal-title']

    infoparser.obj = ''
    infoparser.info['epigraph'] = ''
    r = session.post('http://www.diary.ru/options/diary/?owner')
    infoparser.feed(r.text)
    rezult['epigraph'] = infoparser.info['epigraph']

    return rezult