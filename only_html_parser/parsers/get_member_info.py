import requests
from html.parser import HTMLParser

class memberParser(HTMLParser):
    info = {'favourites':[], 'readers':[], 'communities':[], 'members':[], 'owners':[], 'moderators':[], 'avatar':''}
    obj = ''

    def handle_starttag(self, tag, attrib):
        if tag == 'img' and self.obj == 'avatar':
            if not self.info['avatar']:
                src = next(filter(lambda x: x[0] == 'src', attrib), ('', ''))[1]
                self.info['avatar'] = src
            self.obj = ''
        elif tag == 'h6':
            self.obj = 'header'
    def handle_endtag(self, tag):
        if tag == 'p' and self.obj != 'avatar' :
            self.obj = ''
    def handle_data(self, data):
        data = data.rstrip().lstrip()
        if not data: return

        if data == 'Профиль пользователя':
            self.obj = 'avatar'
        elif self.obj == 'header':
            if data == 'Участники сообщества:':
                self.obj = 'members'
            elif data == 'Участник сообществ:':
                self.obj = 'communities'
            elif data == 'Избранные дневники:':
                self.obj = 'favourites'
            elif data == 'Владельцы сообщества:':
                self.obj = 'owners'
            elif data == 'Модераторы сообщества:':
                self.obj = 'moderators'
            elif data == 'Постоянные читатели:':
                self.obj = 'readers'
            else:
                self.obj = ''
            if self.obj:
                self.obj += '_'
        elif self.obj not in ['', 'avatar']:
            if '_' in self.obj:
                self.obj = self.obj[:-1]
            elif data != ',':
                self.info[self.obj].append(data)

memberparser = memberParser()

def get_info(session, userid):
    r = session.post('http://www.diary.ru/member/?'+userid+'&fullreaderslist&fullfavoriteslist&fullcommunity_membershiplist&fullcommunity_moderatorslist&fullcommunity_masterslist&fullcommunity_memberslist')
    memberparser.feed(r.text)

    return memberparser.info