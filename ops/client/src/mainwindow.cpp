#include "mainwindow.h"
#include "loadchart.h"
#include <QAbstractSocket>
#include <QFormLayout>
#include <QGroupBox>
#include <QHBoxLayout>
#include <QIcon>
#include <QJsonDocument>
#include <QJsonObject>
#include <QLabel>
#include <QLineEdit>
#include <QMessageBox>
#include <QPushButton>
#include <QScrollArea>
#include <QSpinBox>
#include <QStatusBar>
#include <QTabWidget>
#include <QVBoxLayout>
#include <QUrl>
#include <QtEndian>

MainWindow::MainWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowTitle("Yiuk Switchboard 运维中心"); resize(980, 820);
    auto* root = new QWidget(this); auto* rootLayout = new QVBoxLayout(root); rootLayout->setContentsMargins(18,16,18,14);
    auto* title = new QLabel("服务器运维中心"); QFont titleFont=title->font();titleFont.setPointSize(21);titleFont.setBold(true);title->setFont(titleFont);rootLayout->addWidget(title);
    pages=new QTabWidget;rootLayout->addWidget(pages,1);

    auto* serverPage=new QWidget;auto* serverLayout=new QVBoxLayout(serverPage);serverLayout->setSpacing(12);
    auto* statusBox=new QGroupBox("服务器状态");auto* statusLayout=new QHBoxLayout(statusBox);
    processStatus=new QLabel("未登录");cpuStatus=new QLabel("—");memoryStatus=new QLabel("—");
    auto addMetric=[&](const QString& label,QLabel* value){auto* column=new QVBoxLayout;auto* caption=new QLabel(label);caption->setObjectName("hint");value->setObjectName("metric");column->addWidget(caption);column->addWidget(value);statusLayout->addLayout(column,1);};
    addMetric("Relay 进程",processStatus);addMetric("CPU 当前",cpuStatus);addMetric("内存当前",memoryStatus);serverLayout->addWidget(statusBox);
    auto* chartBox=new QGroupBox("服务器负载 · 最近 5 分钟");auto* chartLayout=new QVBoxLayout(chartBox);chart=new LoadChart;chartLayout->addWidget(chart);serverLayout->addWidget(chartBox);

    protectedControls=new QWidget;protectedControls->setEnabled(false);auto* controlsLayout=new QVBoxLayout(protectedControls);controlsLayout->setContentsMargins(0,0,0,0);
    auto* operations=new QGroupBox("Relay 运维");auto* operationLayout=new QHBoxLayout(operations);
    for(const auto& item:{qMakePair(QString("启动"),QString("start")),qMakePair(QString("停止"),QString("stop")),qMakePair(QString("重启"),QString("restart"))}) {
        auto* button=new QPushButton(item.first);button->setIcon(QIcon(QString(":/icons/%1.png").arg(item.second)));button->setIconSize(QSize(20,20));operationLayout->addWidget(button);connect(button,&QPushButton::clicked,this,[this,action=item.second]{command(action);});
    }
    auto* reloadButton=new QPushButton("重新读取配置");reloadButton->setIcon(QIcon(":/icons/reload.png"));reloadButton->setIconSize(QSize(20,20));operationLayout->addWidget(reloadButton);connect(reloadButton,&QPushButton::clicked,this,&MainWindow::loadConfig);controlsLayout->addWidget(operations);

    auto* configBox=new QGroupBox("Relay 配置 · 按项目编辑");auto* configLayout=new QVBoxLayout(configBox);
    auto* scroll=new QScrollArea;scroll->setWidgetResizable(true);auto* formHost=new QWidget;auto* formLayout=new QVBoxLayout(formHost);
    auto spin=[&](QFormLayout* form,const QString& label,int min,int max){auto* field=new QSpinBox;field->setRange(min,max);field->setKeyboardTracking(false);form->addRow(label,field);return field;};

    auto* portsBox=new QGroupBox("端口");auto* portsForm=new QFormLayout(portsBox);
    controlPort=spin(portsForm,"控制端口",1,65535);roomPortMin=spin(portsForm,"房间端口起始",1,65535);roomPortMax=spin(portsForm,"房间端口结束",1,65535);formLayout->addWidget(portsBox);
    auto* authBox=new QGroupBox("密码");auto* authForm=new QFormLayout(authBox);
    hostPasswordConfig=new QLineEdit;hostPasswordConfig->setEchoMode(QLineEdit::Password);hostPasswordConfig->setMaxLength(256);
    playerPasswordConfig=new QLineEdit;playerPasswordConfig->setEchoMode(QLineEdit::Password);playerPasswordConfig->setMaxLength(256);
    authForm->addRow("服主密码",hostPasswordConfig);authForm->addRow("玩家密码",playerPasswordConfig);formLayout->addWidget(authBox);
    auto* limitsBox=new QGroupBox("容量与超时");auto* limitsForm=new QFormLayout(limitsBox);
    maxRooms=spin(limitsForm,"最大房间数",1,65535);maxPlayersPerRoom=spin(limitsForm,"每房间最大玩家数",1,256);maxPendingPerRoom=spin(limitsForm,"每房间等待连接上限",1,65535);
    pendingTimeoutSec=spin(limitsForm,"等待配对超时（秒）",1,86400);reconnectGraceSec=spin(limitsForm,"服主重连宽限（秒）",1,86400);heartbeatTimeoutSec=spin(limitsForm,"心跳超时（秒）",1,86400);
    sessionIdleTimeoutSec=spin(limitsForm,"游戏连接空闲超时（秒）",30,3600);maxConnections=spin(limitsForm,"全局最大连接数",1,65535);authRatePerMin=spin(limitsForm,"每 IP 每分钟登录次数",1,65535);formLayout->addWidget(limitsBox);
    auto* logBox=new QGroupBox("日志");auto* logForm=new QFormLayout(logBox);logFile=new QLineEdit;logForm->addRow("日志文件路径（留空关闭）",logFile);formLayout->addWidget(logBox);formLayout->addStretch();
    scroll->setWidget(formHost);configLayout->addWidget(scroll,1);
    auto* saveRow=new QHBoxLayout;saveRow->addStretch();auto* saveButton=new QPushButton("校验并保存配置");saveButton->setIcon(QIcon(":/icons/save.png"));saveButton->setIconSize(QSize(20,20));saveRow->addWidget(saveButton);configLayout->addLayout(saveRow);connect(saveButton,&QPushButton::clicked,this,&MainWindow::saveConfig);controlsLayout->addWidget(configBox,1);
    serverLayout->addWidget(protectedControls,2);pages->addTab(serverPage,"服务器运维");

    auto* loginPage=new QWidget;auto* loginLayout=new QVBoxLayout(loginPage);loginLayout->addStretch();
    auto* loginBox=new QGroupBox("运维登录");auto* loginForm=new QFormLayout(loginBox);
    url=new QLineEdit("127.0.0.1:29564");url->setPlaceholderText("服务器主机名或 IP:端口");
    hostPassword=new QLineEdit;hostPassword->setEchoMode(QLineEdit::Password);hostPassword->setPlaceholderText("转发后端配置中的 host_password");
    loginForm->addRow("服务器地址",url);loginForm->addRow("服主密码",hostPassword);
    loginButton=new QPushButton("登录并打开运维面板");loginButton->setIcon(QIcon(":/icons/login.png"));loginButton->setIconSize(QSize(20,20));loginForm->addRow("",loginButton);loginLayout->addWidget(loginBox);loginLayout->addStretch();pages->addTab(loginPage,"登录");
    connect(loginButton,&QPushButton::clicked,this,&MainWindow::login);
    setCentralWidget(root);statusBar()->showMessage("先到“登录”页输入 relay 的服主密码；连接使用 TLS 加密 TCP");

    connect(&refreshTimer,&QTimer::timeout,this,&MainWindow::refresh);
    connect(&socket,&QSslSocket::encrypted,this,[this]{flushRequests();statusBar()->showMessage("TLS 加密通道已建立",4000);});
    connect(&socket,&QSslSocket::readyRead,this,&MainWindow::readResponses);
    connect(&socket,&QSslSocket::disconnected,this,[this]{setAuthenticated(false);processStatus->setText("未连接");});
    connect(&socket,&QSslSocket::errorOccurred,this,[this](QAbstractSocket::SocketError){if(!pending.isEmpty())failPending(socket.errorString());});
}

void MainWindow::showError(const QString& message) { QMessageBox::warning(this,"运维请求失败",message);statusBar()->showMessage(message,8000); }

void MainWindow::request(const QString& type,const QByteArray& body,std::function<void(int,const QByteArray&)> done) {
    QJsonObject message;const quint32 id=nextRequestId++;message.insert("version",1);message.insert("id",static_cast<qint64>(id));message.insert("type",type);
    if(!body.isEmpty()) {auto parsed=QJsonDocument::fromJson(body);if(!parsed.isObject()){done(400,"invalid request payload");return;}const auto fields=parsed.object();for(auto it=fields.begin();it!=fields.end();++it)message.insert(it.key(),it.value());}
    QByteArray payload=QJsonDocument(message).toJson(QJsonDocument::Compact);if(payload.size()>(2<<20)){done(413,"request too large");return;}
    QByteArray frame(4,Qt::Uninitialized);qToBigEndian<quint32>(static_cast<quint32>(payload.size()),reinterpret_cast<uchar*>(frame.data()));frame+=payload;
    pending.insert(id,std::move(done));outgoing.enqueue(frame);
    if(socket.state()==QAbstractSocket::UnconnectedState) {
        QUrl endpoint("tcp://"+url->text().trimmed());
        if(endpoint.host().isEmpty()||endpoint.port()<1||endpoint.port()>65535){failPending("请输入有效的主机名:端口，例如 ops.example.com:29564");return;}
        socket.setPeerVerifyMode(QSslSocket::VerifyPeer);socket.connectToHostEncrypted(endpoint.host(),static_cast<quint16>(endpoint.port()));
    }
    if(socket.isEncrypted())flushRequests();
}

void MainWindow::flushRequests() {
    while(socket.isEncrypted()&&!outgoing.isEmpty()) {QByteArray frame=outgoing.dequeue();if(socket.write(frame)!=frame.size()){failPending(socket.errorString().isEmpty()?"发送运维请求失败":socket.errorString());return;}}
}

void MainWindow::readResponses() {
    incoming+=socket.readAll();
    while(incoming.size()>=4) {
        const quint32 length=qFromBigEndian<quint32>(reinterpret_cast<const uchar*>(incoming.constData()));
        if(length==0||length>(2<<20)){socket.abort();failPending("服务端返回了无效帧");return;}
        if(incoming.size()<static_cast<qsizetype>(4+length))return;
        const QByteArray payload=incoming.mid(4,length);incoming.remove(0,4+length);
        const QJsonObject response=QJsonDocument::fromJson(payload).object();const quint32 id=static_cast<quint32>(response.value("id").toInteger());
        auto it=pending.find(id);if(it==pending.end())continue;
        auto callback=std::move(it.value());pending.erase(it);
        if(!response.value("ok").toBool()){callback(500,response.value("error").toString().toUtf8());continue;}
        callback(200,QJsonDocument(response.value("data").toObject()).toJson(QJsonDocument::Compact));
    }
}

void MainWindow::failPending(const QString& error) {
    outgoing.clear();auto callbacks=std::move(pending);pending.clear();
    for(auto it=callbacks.begin();it!=callbacks.end();++it)it.value()(500,error.toUtf8());
}

void MainWindow::setAuthenticated(bool value) {
    authenticated=value;protectedControls->setEnabled(value);loginButton->setEnabled(!value);
    if(value){pages->setCurrentIndex(0);}else if(pages->currentIndex()==0&&pages->count()>1){pages->setCurrentIndex(1);refreshTimer.stop();}
}

void MainWindow::login() {
    if(hostPassword->text().isEmpty()){showError("请输入转发后端的服主密码");return;}
    loginButton->setEnabled(false);statusBar()->showMessage("正在验证服主密码…");
    QJsonObject credentials;credentials.insert("password",hostPassword->text());
    request("authenticate",QJsonDocument(credentials).toJson(QJsonDocument::Compact),[this](int code,const QByteArray& response){
        if(code!=200){loginButton->setEnabled(true);authenticated=false;showError(QString::fromUtf8(response));return;}
        setAuthenticated(true);statusBar()->showMessage("服主密码验证成功",5000);refreshTimer.start(5000);refresh();loadConfig();
    });
}

void MainWindow::command(const QString& action) {
    if(!authenticated){showError("请先完成服主密码登录");return;}
    QJsonObject body;body.insert("action",action);
    request("process",QJsonDocument(body).toJson(QJsonDocument::Compact),[this,action](int code,const QByteArray& bytes){
        if(code<200||code>=300){showError(QString::fromUtf8(bytes));return;}
        statusBar()->showMessage(QString("Relay %1 指令已完成").arg(action),5000);refresh();
    });
}

void MainWindow::refresh() {
    if(!authenticated)return;
    request("status",{},[this](int code,const QByteArray& bytes){if(code!=200)return;auto j=QJsonDocument::fromJson(bytes).object();processStatus->setText(j.value("running").toBool()?QString("运行中 · PID %1").arg(j.value("pid").toInt()):"已停止");});
    request("metrics",{},[this](int code,const QByteArray& bytes){
        if(code!=200)return;auto j=QJsonDocument::fromJson(bytes).object();
        if(!j.value("available").toBool()){cpuStatus->setText("不可用");memoryStatus->setText("不可用");chart->append(-1,-1);return;}
        const double cpuValue=j.value("cpu_percent").toDouble();const double total=j.value("memory_total_bytes").toDouble(),used=j.value("memory_used_bytes").toDouble();
        cpuStatus->setText(QString::number(cpuValue,'f',1)+"%");memoryStatus->setText(total>0?QString("%1 / %2 GB").arg(used/1073741824.0,0,'f',1).arg(total/1073741824.0,0,'f',1):"不可用");
        chart->append(cpuValue,total>0?used*100.0/total:-1);
    });
}

QJsonObject MainWindow::configFields() const {
    QJsonObject c;
    c.insert("control_port",controlPort->value());c.insert("room_port_min",roomPortMin->value());c.insert("room_port_max",roomPortMax->value());
    c.insert("host_password",hostPasswordConfig->text());c.insert("player_password",playerPasswordConfig->text());
    c.insert("max_rooms",maxRooms->value());c.insert("max_players_per_room",maxPlayersPerRoom->value());c.insert("max_pending_per_room",maxPendingPerRoom->value());
    c.insert("pending_timeout_sec",pendingTimeoutSec->value());c.insert("reconnect_grace_sec",reconnectGraceSec->value());c.insert("heartbeat_timeout_sec",heartbeatTimeoutSec->value());
    c.insert("session_idle_timeout_sec",sessionIdleTimeoutSec->value());c.insert("max_connections",maxConnections->value());c.insert("auth_rate_per_min",authRatePerMin->value());c.insert("log_file",logFile->text());
    return c;
}

void MainWindow::setConfig(const QJsonObject& c) {
    controlPort->setValue(c.value("control_port").toInt());roomPortMin->setValue(c.value("room_port_min").toInt());roomPortMax->setValue(c.value("room_port_max").toInt());
    hostPasswordConfig->setText(c.value("host_password").toString());playerPasswordConfig->setText(c.value("player_password").toString());
    maxRooms->setValue(c.value("max_rooms").toInt());maxPlayersPerRoom->setValue(c.value("max_players_per_room").toInt());maxPendingPerRoom->setValue(c.value("max_pending_per_room").toInt());
    pendingTimeoutSec->setValue(c.value("pending_timeout_sec").toInt());reconnectGraceSec->setValue(c.value("reconnect_grace_sec").toInt());heartbeatTimeoutSec->setValue(c.value("heartbeat_timeout_sec").toInt());
    sessionIdleTimeoutSec->setValue(c.value("session_idle_timeout_sec").toInt());maxConnections->setValue(c.value("max_connections").toInt());authRatePerMin->setValue(c.value("auth_rate_per_min").toInt());logFile->setText(c.value("log_file").toString());
}

void MainWindow::loadConfig() {
    if(!authenticated){showError("请先完成服主密码登录");return;}
    request("config_get",{},[this](int code,const QByteArray& bytes){if(code!=200){showError(QString::fromUtf8(bytes));return;}setConfig(QJsonDocument::fromJson(bytes).object());statusBar()->showMessage("已读取配置项",5000);});
}

void MainWindow::saveConfig() {
    if(!authenticated){showError("请先完成服主密码登录");return;}
    if(QMessageBox::question(this,"保存服务器配置","确认校验并保存这些配置项？当前 relay 进程不会自动重启。")!=QMessageBox::Yes)return;
    QJsonObject body;body.insert("config",configFields());
    request("config_set",QJsonDocument(body).toJson(QJsonDocument::Compact),[this](int code,const QByteArray& bytes){if(code<200||code>=300){showError(QString::fromUtf8(bytes));return;}statusBar()->showMessage("配置项已校验并保存；如需应用请手动重启 Relay",7000);});
}
