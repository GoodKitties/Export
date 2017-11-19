import requests
from html.parser import HTMLParser

class linkParser(HTMLParser):
    links = {'posts':[], 'activ':'', 'diary': ''}
    info = {}
    obj = ''

    def handle_starttag(self, tag, attrib):
        if tag =='div':
            cl = next(filter(lambda x: x[0] == 'class', attrib), ('', ''))[1]
            if 'pageBar' in cl:
                self.obj = 'pagebar'
        elif tag =='ul':
            id = next(filter(lambda x: x[0] == 'id', attrib), ('', ''))[1]
            if id == 'inf_menu':
                self.obj = 'name'
            elif id == 'm_menu':
                self.obj = 'diary'
        elif tag =='li':
            cl = next(filter(lambda x: x[0] == 'class', attrib), ('', ''))[1]
            if 'editPostLink' in cl:
                self.obj = 'editpost'
        elif tag == 'td':
            if 'pagebar' in self.obj:
                self.obj += '_'
        elif tag == 'a':
            href = next(filter(lambda x: x[0] == 'href', attrib), ('', ''))[1]
            if self.obj == 'pagebar__': #следующая страница
                self.links['activ'] = href
                self.obj = ''
            elif self.obj == 'editpost':
                self.links['posts'].append(href[href.rfind('=')+1:])
                self.obj = ''
            elif self.obj == 'name':
                self.info['userid'] = href[href.find('?')+1:]
            elif self.obj == 'diary':
                self.info['shortname'] = href[0 + 7 * ('http://' in href):href.find('.diary.ru')]
                self.links['diary'] = href
        pass
    def handle_endtag(self, tag):
        pass
    def handle_data(self, data):
        data = data.rstrip().lstrip()
        if not data: return

        if self.obj == 'name':
            self.info['username'] = data
            self.obj = ''
        elif self.obj == 'diary':
            if data == 'Мой дневник':
                self.info['journal'] = '1'
            elif data == 'Мое сообщество':
                self.info['journal'] = '2'
            else:
                self.info['journal'] = '0'
            self.obj = ''

linkparser = linkParser()

def get_diary(session, label):
    r = session.post('http://www.diary.ru/')
    linkparser.feed(r.text)
    linkparser.links['activ'] = ''
    linkparser.obj = ''
    return linkparser.info

def get_posts_links(session, label):
    linkparser.links['activ'] = linkparser.links['diary']
    print('\nСобираем информацию о записях...')

    while linkparser.links['activ']:
        r = session.get(linkparser.links['activ'])
        linkparser.links['activ'] = ''
        linkparser.obj = ''
        linkparser.feed(r.text)
    if 'http://www.diary.ru/options/diary/?owner' in linkparser.links['posts']:
        linkparser.links['posts'].remove('http://www.diary.ru/options/diary/?owner')

    return linkparser.links['posts']