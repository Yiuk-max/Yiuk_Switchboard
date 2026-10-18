#include "mainwindow.h"
#include <QApplication>
#include <QFile>
#include <QIcon>
#include <QRandomGenerator>

int main(int argc,char** argv) {
    QApplication app(argc,argv);QFile style(":/style.qss");if(style.open(QIODevice::ReadOnly))app.setStyleSheet(QString::fromUtf8(style.readAll()));
    const char* steveIcons[]={":/icons/steve-portrait.png",":/icons/steve-head.png"};
    const QIcon windowIcon(steveIcons[QRandomGenerator::global()->bounded(2)]);
    app.setWindowIcon(windowIcon);
    MainWindow window;window.setWindowIcon(windowIcon);window.show();return app.exec();
}
