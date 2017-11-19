import sys
from PyQt5.QtWidgets import *
from PyQt5.QtCore import *
from PyQt5.QtGui import *
import parser_without_api

class App(QMainWindow):
    def __init__(self):
        super().__init__()
        self.initUI()

    def initUI(self):
        self.setWindowTitle('diary export')
        self.resize(420, 400)

        label = QLabel(self)
        label.move(20, 0)
        label.resize(400, 80)
        label.setText('КОМАНДА ДЫБРА ПРИВЕТСТВУЕТ ВАС!'
                      '\n\nПожалуйста, введите данные своей учетной записи.'
                      '\nПароль необходим для выгрузки дневника и не будет записан где-либо.')

        label = QLabel(self)
        label.move(20, 90)
        label.resize(80, 20)
        label.setText('логин:')

        label = QLabel(self)
        label.move(20, 120)
        label.resize(80, 20)
        label.setText('пароль:')

        self.textbox_login = QLineEdit(self)
        self.textbox_login.move(100, 90)
        self.textbox_login.resize(300, 20)

        self.textbox_pass = QLineEdit(self)
        self.textbox_pass.move(100, 120)
        self.textbox_pass.resize(300, 20)

        label = QLabel(self)
        label.move(20, 160)
        label.resize(200, 20)
        label.setText('Директория для выгружаемых файлов:')

        self.textbox_path = QLineEdit(self)
        self.textbox_path.move(20, 190)
        self.textbox_path.resize(330, 30)

        btn = QPushButton('...', self)
        btn.move(370, 190)
        btn.resize(30, 30)
        btn.clicked.connect(self.get_dir)

        self.btn = QPushButton('Начать', self)
        self.btn.move(170, 260)
        self.btn.resize(100, 40)
        self.btn.clicked.connect(self.call_parser)

        self.label = QLabel(self)
        self.label.move(20, 290)
        self.label.resize(800, 120)

        self.show()

    def get_dir(self):
        path = QFileDialog.getExistingDirectory()
        self.textbox_path.setText(path)

    def call_parser(self):
        self.btn.setEnabled(False)
        self.obj = parser_without_api.Exporter(self.textbox_login.text(),
                                 self.textbox_pass.text(),
                                 self.textbox_path.text())
        self.thread = QThread(self)
        self.obj.moveToThread(self.thread)
        self.thread.started.connect(self.obj.make_all)
        self.obj.message.connect(self.p)
        self.obj.finished.connect(self.thread.quit)
        self.obj.finished.connect(self.obj.deleteLater)
        self.obj.finished.connect(self.q)
        self.thread.finished.connect(self.thread.deleteLater)
        self.thread.start()
    def p(self, n):
        self.label.setText(n)
    def q(self):
        self.btn.setEnabled(True)

if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = App()
    sys.exit(app.exec_())