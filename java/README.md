﻿Инструкция

* установить java, если не установлено https://java.com/ru/download/
* выгрузить к себе файл [diary-exporter.jar](https://github.com/GoodKitties/Export/raw/master/java/diary-exporter.jar)
* запустить diary-exporter.jar
* ввести требуемые данные
* результаты будут сложены в папку diary\_*shortname* внутри выбранного вами каталога (*shortname* - короткое имя выгружаемого аккаунта)
* html страницы складываются в папку diary\_*shortname*\_html рядом с папкой diary\_*shortname*
* архив zip в каталоге экспорта пригоден для импорта в сервис dybr.ru

   
Если в вашем дневнике включена опция "Использовать только любимые темы", то выгружены будут только теги из данного списка.

Подключение через прокси не устанавливается.

В случае, если ваш кириллический логин отображается через проценты, поставьте галочку "дополнить существующие файлы" и произведите выкачку, а затем заново сформируйте html-файлы
