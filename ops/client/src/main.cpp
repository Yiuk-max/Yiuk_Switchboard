#include "mainwindow.h"
#include <QApplication>
#include <QFile>

int main(int argc,char** argv) {
    QApplication app(argc,argv);QFile style(":/style.qss");if(style.open(QIODevice::ReadOnly))app.setStyleSheet(QString::fromUtf8(style.readAll()));
    MainWindow window;window.show();return app.exec();
}
