import requests
import re
from copy import copy
from html.parser import HTMLParser

class postParser(HTMLParser):
    post = {'tags': [], 'comments': [], 'no_comments': '0', 'access':'0'}
    new_comment = {'author_username':'', 'message_html':'', 'dateline':''}
    comment = {}
    answer = {}
    obj = ''
    cmmnt_data = -2

    def handle_starttag(self, tag, attrib):
        if self.cmmnt_data >= 0:
            if self.cmmnt_data:
                self.comment['message_html'] += '<' + tag + ' ' + ' '.join([a[0]+('=\"'+str(a[1])+'\"')*(not a[1] is None) for a in attrib]) + '>'
            if tag == 'div':
                self.cmmnt_data += 1
        elif tag == 'textarea':
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            if name == 'message':
                self.obj = 'post'
            elif name == 'access_list3':
                self.obj = 'accesslist'
        elif tag =='div':
            id = next(filter(lambda x: x[0] == 'id', attrib), ('', ''))[1]
            cl = next(filter(lambda x: x[0] == 'class', attrib), ('', ''))[1]
            if id == 'atTagBox':
                self.obj = 'tags'
            elif 'postDate' in cl:
                self.obj = 'postdate'
            elif 'voting' in cl and 'voting' in self.post:
                self.obj = 'voting'
            elif id == 'commentsArea':
                self.obj = 'comments'
            elif self.obj == 'comments':
                cl = next(filter(lambda x: x[0] == 'class', attrib), ('', ''))[1]
                if 'singleComment' in cl:
                    self.comment = copy(self.new_comment)
                elif 'authorName' in cl:
                    self.obj += '_author'
                elif 'postTitle' in cl:
                    self.obj += '_date'
                elif 'paragraph' in cl:
                    self.cmmnt_data = 0
                    self.obj += '_message'
        elif tag == 'input':
            id = next(filter(lambda x: x[0] == 'id', attrib), ('', ''))[1]
            value = next(filter(lambda x: x[0] == 'value', attrib), ('', ''))[1]
            checked = next(filter(lambda x: x[0] == 'checked', attrib), None)
            name = next(filter(lambda x: x[0] == 'name', attrib), ('', ''))[1]
            if id == 'postTitle':
                self.post['title'] = value
            elif self.obj == 'tags' and checked:
                self.post['tags'].append(value)
            elif name == 'themes' and value:
                self.post['tags'] += value.split(';')
            elif name == 'current_music' and value:
                self.post['current_music'] = value
            elif name == 'current_mood' and value:
                self.post['current_mood'] = value
            elif name == 'poll_title' and value:
                self.post['voting'] = {}
                self.post['voting']['question'] = value
                self.post['voting']['answers'] = []
            elif name == 'multiselect':
                self.post.get('voting', {})['multiselect'] = checked != None
            elif name == 'end_poll':
                self.post.get('voting', {})['end'] = checked != None
            elif name == 'no_comments' and checked:
                self.post['no_comments'] = '1'
            elif 'close_access_mode' in name and checked:
                self.post['access'] = str(int(self.post['access']) + int(value))
        elif tag == 'span':
            if 'postdate' in self.obj:
                self.obj += '_'
            elif self.obj == 'voting':
                id = next(filter(lambda x: x[0] == 'id', attrib), ('', ''))[1]
                if 'spanpollaction' in id:
                    self.obj += '_link'
        elif tag == 'a':
            title = next(filter(lambda x: x[0] == 'title', attrib), ('', ''))[1]
            onclick = next(filter(lambda x: x[0] == 'onclick', attrib), ('', ''))[1]
            if title == 'вернуть на место' and \
                            onclick == 'return confirm("Вы уверены, что хотите вернуть запись на место?");':
                self.obj = 'postcdate'
            elif self.obj == 'voting_link':
                self.post['voting']['link'] = next(filter(lambda x: x[0] == 'href', attrib), ('', ''))[1]
                self.obj = ''
        elif tag == 'table' and self.obj == 'voting':
            self.answer = {}
        elif tag == 'tr' and self.obj == 'voting':
            if self.answer:
                if all([x in self.answer for x in ['variant', 'count', 'percent']]):
                    self.post['voting']['answers'].append(self.answer)
                else:
                    self.obj = ''
            self.answer = {}
    def handle_endtag(self, tag):
        if tag == 'ul' and self.obj == 'tags':
            self.obj = ''
        elif tag == 'div' and self.obj == 'accesslist':
            self.obj = ''
        elif self.cmmnt_data >= 0:
            if tag == 'div':
                self.cmmnt_data -= 1
            if self.cmmnt_data > 0:
                self.comment['message_html'] += '</' + tag + '>'
            elif not self.cmmnt_data:
                diaryname = '0-9a-zA-Zа-яА-Я\-\_\~\!\@\#\$\&\*\(\)\+\?\=\/\|\\\.\,\;\:\<\>\[\] '
                diarylink = '0-9a-zA-Z\-\_\.\!\~\*\'\\(\)\/\:'
                fullJ = r'(?:<a class="TagJIco" href="(?:http://www.diary.ru|)/member/\?[0-9]+" title="профиль" target=(?:\'|\")?_blank(?:\'|\")?>(?:&nbsp;|)</a>|)<a class="TagL" href="[' + diarylink + ']+.diary.ru" title="(?:дневник: |)[' + diaryname + ']+" target=(?:\'|\")?_blank(?:\'|\")?>([' + diaryname + ']+)</a>'
                openMORE = r'(<a href="(?:/~[" + diarylink + "]+/p[0-9]+.htm\?oam|)#more[0-9]*" class="LinkMore" onclick="var e=event; if \(swapMore\(\"c?[0-9]+m[0-9]+\", e.ctrlKey \|\| e.altKey\)\) document.location = this.href; return false;" id="linkmorec?[0-9]+m[0-9]+">)'
                startMORE = r'(</a><span id="morec?[0-9]+m[0-9]"+ ondblclick="return swapMore\(\"c?[0-9]+m[0-9]+\"\);" style="display:none;visibility:hidden;"><a name="morec?[0-9]+m[0-9]+start"></a>)'
                closeMORE = r'<a name="morec?[0-9]+m[0-9]+end"></a></span>'
                body = self.comment.pop('message_html', '')
                body = re.sub(fullJ, r'[J]\1[/J]', body)
                body = re.sub(openMORE, '[MORE=', body)
                body = re.sub(startMORE, ']', body)
                body = re.sub(closeMORE, '[/MORE]', body)
                self.comment['message_html'] = body
                self.post['comments'].append(self.comment)
                self.cmmnt_data = -2
                self.obj = 'comments'
    def handle_data(self, data):
        data = data.rstrip().lstrip()
        if not data: return

        if self.obj == 'post':
            if 'message_html' not in self.post:
                self.post['message_html'] = data
            self.obj = ''
        elif self.obj == 'accesslist':
            self.post['access_list'] = data.split('\n')
        elif self.obj == 'postdate_':
            self.post['dateline_date'] = data
        elif self.obj == 'postdate__':
            self.post['dateline_date'] += ', '+data
            if 'dateline_cdate' not in self.post:
                self.post['dateline_cdate'] = self.post['dateline_date']
            self.obj = ''
        elif self.obj == 'postcdate':
            self.post['dateline_cdate'] = data[data.find(': ')+2:]
            self.obj = ''
        elif self.obj == 'comments_author':
            self.comment['author_username'] = data
            self.obj = 'comments'
        elif self.obj == 'comments_message':
            if self.cmmnt_data > 0:
                self.comment['message_html'] += data
        elif self.obj == 'comments_date':
            self.comment['dateline'] = data
            self.obj = 'comments'
        elif self.obj == 'voting':
            if 'variant' not in self.answer:
                self.answer['variant'] = data[3:].lstrip().rstrip()
            elif 'count' not in self.answer:
                self.answer['count'] = data
            elif 'percent' not in self.answer:
                self.answer['percent'] = data


postparser = postParser()

def get_posts(session, diary, posts, count, ready, label):
    for i, post in enumerate(posts):
        r = session.get(diary+'/?editpost&postid='+post+'')
        postparser.feed(r.text)
        r = session.get(diary+'/p'+post+'.htm')
        postparser.feed(r.text)
        if 'link' in postparser.post.get('voting', {}):
            r = session.get(postparser.post['voting'].pop('link'))
            postparser.obj = 'voting'
            postparser.feed(r.text)
        while '' in postparser.post['tags']:
            postparser.post['tags'].remove('')
        posts[i] = postparser.post
        posts[i]['postid'] = post
        postparser.post = {'tags': [], 'comments': [], 'no_comments': '0', 'access':'0'}
        postparser.obj = ''
        print('\rПолучено ' + str(int(100 * (ready+i+1) / count)) + '% \t('+str(ready+i+1)+' из '+str(count)+')', end='', flush=True)

    return posts
