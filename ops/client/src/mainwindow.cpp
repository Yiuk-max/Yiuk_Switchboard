#include "mainwindow.h"
#include "loadchart.h"
#include <QAbstractSocket>
#include <QCheckBox>
#include <QCryptographicHash>
#include <QFormLayout>
#include <QGroupBox>
#include <QHBoxLayout>
#include <QIcon>
#include <QJsonDocument>
#include <QJsonObject>
#include <QLabel>
#include <QLineEdit>
#include <QMessageBox>
#include <QNetworkProxy>
#include <QPushButton>
#include <QScrollArea>
#include <QSslError>
#include <QSslCertificate>
#include <QSettings>
#include <QSpinBox>
#include <QStatusBar>
#include <QStringList>
#include <QTabWidget>
#include <QVBoxLayout>
#include <QUrl>
#include <QWheelEvent>
#include <QtEndian>
#ifdef Q_OS_WIN
#include <windows.h>
#include <wincrypt.h>
#endif

namespace {
class NoWheelSpinBox : public QSpinBox {
public:
    using QSpinBox::QSpinBox;
protected:
    void wheelEvent(QWheelEvent* event) override { event->ignore(); }
};

QSettings appSettings() { return QSettings("YiukSwitchboard","YiukOps"); }

bool protectPassword(const QString& password,QByteArray& protectedValue) {
#ifdef Q_OS_WIN
    const QByteArray plain=password.toUtf8();
    DATA_BLOB input{static_cast<DWORD>(plain.size()),reinterpret_cast<BYTE*>(const_cast<char*>(plain.constData()))};
    DATA_BLOB output{};
    if(!CryptProtectData(&input,L"Yiuk Ops credentials",nullptr,nullptr,nullptr,CRYPTPROTECT_UI_FORBIDDEN,&output))return false;
    protectedValue=QByteArray(reinterpret_cast<const char*>(output.pbData),static_cast<qsizetype>(output.cbData));
    LocalFree(output.pbData);
    return true;
#else
    Q_UNUSED(password);Q_UNUSED(protectedValue);
    return false;
#endif
}

bool unprotectPassword(const QByteArray& protectedValue,QString& password) {
#ifdef Q_OS_WIN
    if(protectedValue.isEmpty())return false;
    DATA_BLOB input{static_cast<DWORD>(protectedValue.size()),reinterpret_cast<BYTE*>(const_cast<char*>(protectedValue.constData()))};
    DATA_BLOB output{};
    if(!CryptUnprotectData(&input,nullptr,nullptr,nullptr,nullptr,CRYPTPROTECT_UI_FORBIDDEN,&output))return false;
    password=QString::fromUtf8(reinterpret_cast<const char*>(output.pbData),static_cast<qsizetype>(output.cbData));
    LocalFree(output.pbData);
    return true;
#else
    Q_UNUSED(protectedValue);Q_UNUSED(password);
    return false;
#endif
}
}

MainWindow::MainWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowTitle("Yiuk Switchboard 运维中心"); resize(980, 820);
    auto* root = new QWidget(this); auto* rootLayout = new QVBoxLayout(root); rootLayout->setContentsMargins(18,16,18,14);
    pages=new QTabWidget;rootLayout->addWidget(pages,1);

    auto* serverPage=new QWidget;auto* serverLayout=new QVBoxLayout(serverPage);serverLayout->setSpacing(12);
    operationControls=new QWidget;operationControls->setEnabled(false);
    auto* operationOuterLayout=new QVBoxLayout(operationControls);operationOuterLayout->setContentsMargins(0,0,0,0);
    auto* operations=new QGroupBox("Relay 运维");auto* operationLayout=new QHBoxLayout(operations);
    for(const auto& item:{qMakePair(QString("启动"),QString("start")),qMakePair(QString("停止"),QString("stop")),qMakePair(QString("重启"),QString("restart"))}) {
        auto* button=new QPushButton(item.first);button->setIcon(QIcon(QString(":/icons/%1.png").arg(item.second)));button->setIconSize(QSize(20,20));operationLayout->addWidget(button);connect(button,&QPushButton::clicked,this,[this,action=item.second]{command(action);});
    }
    auto* reloadButton=new QPushButton("重新读取配置");reloadButton->setIcon(QIcon(":/icons/reload.png"));reloadButton->setIconSize(QSize(20,20));operationLayout->addWidget(reloadButton);connect(reloadButton,&QPushButton::clicked,this,&MainWindow::loadConfig);operationOuterLayout->addWidget(operations);
    serverLayout->addWidget(operationControls);
    auto* chartBox=new QGroupBox("服务器负载 · 最近 5 分钟");auto* chartLayout=new QVBoxLayout(chartBox);chart=new LoadChart;chartLayout->addWidget(chart);serverLayout->addWidget(chartBox);

    protectedControls=new QWidget;protectedControls->setEnabled(false);auto* controlsLayout=new QVBoxLayout(protectedControls);controlsLayout->setContentsMargins(0,0,0,0);
    auto* configBox=new QGroupBox("Relay 配置 · 按项目编辑");auto* configLayout=new QVBoxLayout(configBox);
    auto* scroll=new QScrollArea;scroll->setWidgetResizable(true);auto* formHost=new QWidget;auto* formLayout=new QVBoxLayout(formHost);
    auto spin=[&](QFormLayout* form,const QString& label,int min,int max){auto* field=new NoWheelSpinBox;field->setRange(min,max);field->setKeyboardTracking(false);form->addRow(label,field);return field;};

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
    auto* logBox=new QGroupBox("日志");auto* logForm=new QFormLayout(logBox);logFile=new QLineEdit;logForm->addRow("日志文件路径（留空关闭）",logFile);formLayout->addWidget(logBox);
    auto* saveRow=new QHBoxLayout;saveRow->addStretch();auto* saveButton=new QPushButton("校验并保存配置");saveButton->setIcon(QIcon(":/icons/save.png"));saveButton->setIconSize(QSize(20,20));saveRow->addWidget(saveButton);formLayout->addLayout(saveRow);connect(saveButton,&QPushButton::clicked,this,&MainWindow::saveConfig);
    scroll->setWidget(formHost);configLayout->addWidget(scroll,1);controlsLayout->addWidget(configBox,1);
    serverLayout->addWidget(protectedControls,2);pages->addTab(serverPage,"服务器运维");

    auto* loginPage=new QWidget;auto* loginLayout=new QVBoxLayout(loginPage);loginLayout->addStretch();
    auto* loginBox=new QGroupBox("运维登录");auto* loginForm=new QFormLayout(loginBox);
    url=new QLineEdit("127.0.0.1:29564");url->setPlaceholderText("服务器主机名或 IP:端口");
    hostPassword=new QLineEdit;hostPassword->setEchoMode(QLineEdit::Password);hostPassword->setPlaceholderText("转发后端配置中的 host_password");
    auto* networkNotice=new QLabel("虚拟网卡模式转发可能导致连接失败");
    networkNotice->setObjectName("hint");
    rememberCredentials=new QCheckBox("记住服务器地址和服主密码（仅本机）");
    loginForm->addRow("服务器地址",url);loginForm->addRow("服主密码",hostPassword);
    loginForm->addRow("",networkNotice);
    loginForm->addRow("",rememberCredentials);
    loginButton=new QPushButton("登录并打开运维面板");loginButton->setIcon(QIcon(":/icons/login.png"));loginButton->setIconSize(QSize(20,20));loginForm->addRow("",loginButton);loginLayout->addWidget(loginBox);loginLayout->addStretch();pages->addTab(loginPage,"登录");
    {
        auto settings=appSettings();
        const bool remembered=settings.value("credentials/remember",false).toBool();
        rememberCredentials->setChecked(remembered);
        if(remembered) {
            url->setText(settings.value("credentials/serverAddress",url->text()).toString());
            QString savedPassword;
            if(unprotectPassword(settings.value("credentials/passwordProtected").toByteArray(),savedPassword))hostPassword->setText(savedPassword);
            else settings.remove("credentials/passwordProtected");
        }
    }
    connect(loginButton,&QPushButton::clicked,this,&MainWindow::login);
    setCentralWidget(root);statusBar()->showMessage("先到“登录”页输入 relay 的服主密码；连接使用 TLS 加密 TCP");

    connect(&refreshTimer,&QTimer::timeout,this,&MainWindow::refresh);
    connectTimeoutTimer.setSingleShot(true);
    connectTimeoutTimer.setInterval(30000);
    connect(&connectTimeoutTimer,&QTimer::timeout,this,[this]{
        if(socket.isEncrypted()||socket.state()==QAbstractSocket::UnconnectedState)return;
        failPending("连接或 TLS 握手超过 30 秒仍未完成");
        socket.abort();
    });
    connect(&socket,&QSslSocket::encrypted,this,[this]{connectTimeoutTimer.stop();tlsEstablished=true;sslFailureDetails.clear();flushRequests();statusBar()->showMessage("TLS 加密通道已建立",4000);});
    connect(&socket,&QSslSocket::sslErrors,this,[this](const QList<QSslError>& errors){
        QStringList details;
        for(const auto& error:errors)details.push_back(error.errorString());
        sslFailureDetails=details.join("；");

        const auto certificate=socket.peerCertificate();
        if(certificate.isNull())return;
        bool safeToPin=true;
        for(const auto& error:errors) {
            switch(error.error()) {
            case QSslError::CertificateNotYetValid:
            case QSslError::CertificateExpired:
            case QSslError::InvalidNotBeforeField:
            case QSslError::InvalidNotAfterField:
            case QSslError::CertificateRevoked:
            case QSslError::CertificateSignatureFailed:
            case QSslError::UnableToDecryptCertificateSignature:
            case QSslError::InvalidCaCertificate:
            case QSslError::CertificateBlacklisted:
                safeToPin=false;
                break;
            default: break;
            }
        }
        if(!safeToPin)return;

        const QByteArray fingerprint=certificate.digest(QCryptographicHash::Sha256);
        const QString fingerprintText=QString::fromLatin1(fingerprint.toHex(':')).toUpper();
        const QString pinKey="tlsPins/"+url->text().trimmed();
        QSettings settings("YiukSwitchboard","YiukOps");
        const QByteArray trustedFingerprint=settings.value(pinKey).toByteArray();
        if(!trustedFingerprint.isEmpty()) {
            if(trustedFingerprint==fingerprint) {
                socket.ignoreSslErrors(errors);
                sslFailureDetails.clear();
            } else {
                sslFailureDetails="此服务器证书与已固定的指纹不一致。当前 SHA-256 指纹："+fingerprintText;
            }
            return;
        }

        const QString subject=certificate.subjectInfo(QSslCertificate::CommonName).join(", ");
        const auto answer=QMessageBox::question(this,"首次信任服务器证书",
            QString("服务器证书未通过系统 CA 或地址名称校验。\n校验信息：%3\n证书名称：%1\nSHA-256：\n%2\n\n请先核对指纹；只有确认这是自己的服务器时才继续。是否固定并信任这张证书？")
                .arg(subject.isEmpty()?"（无）":subject,fingerprintText,sslFailureDetails),
            QMessageBox::Yes|QMessageBox::No,QMessageBox::No);
        if(answer==QMessageBox::Yes) {
            settings.setValue(pinKey,fingerprint);
            socket.ignoreSslErrors(errors);
            sslFailureDetails.clear();
            statusBar()->showMessage("已固定此服务器的 TLS 证书指纹",5000);
        } else {
            sslFailureDetails="你拒绝了此服务器证书；SHA-256："+fingerprintText;
        }
    });
    connect(&socket,&QSslSocket::readyRead,this,&MainWindow::readResponses);
    connect(&socket,&QSslSocket::disconnected,this,[this]{setAuthenticated(false);});
    connect(&socket,&QSslSocket::errorOccurred,this,[this](QAbstractSocket::SocketError){
        connectTimeoutTimer.stop();
        if(pending.isEmpty())return;
        QString error=socket.errorString();
        error+=tlsEstablished?"\nTLS 已建立，对端随后关闭连接":"\n连接在 TLS 握手完成前关闭";
        if(!sslFailureDetails.isEmpty())error+="\nTLS 证书校验："+sslFailureDetails;
        failPending(error);
    });
}

void MainWindow::showError(const QString& message) {
    // Keep failures out of a nested modal event loop while the TLS socket is
    // resolving callbacks; the status bar keeps the error visible without
    // re-entering request processing.
    statusBar()->showMessage("运维请求失败："+message,15000);
}

void MainWindow::request(const QString& type,const QByteArray& body,std::function<void(int,const QByteArray&)> done) {
    QJsonObject message;const quint32 id=nextRequestId++;message.insert("version",1);message.insert("id",static_cast<qint64>(id));message.insert("type",type);
    if(!body.isEmpty()) {auto parsed=QJsonDocument::fromJson(body);if(!parsed.isObject()){done(400,"invalid request payload");return;}const auto fields=parsed.object();for(auto it=fields.begin();it!=fields.end();++it)message.insert(it.key(),it.value());}
    QByteArray payload=QJsonDocument(message).toJson(QJsonDocument::Compact);if(payload.size()>(2<<20)){done(413,"request too large");return;}
    QByteArray frame(4,Qt::Uninitialized);qToBigEndian<quint32>(static_cast<quint32>(payload.size()),reinterpret_cast<uchar*>(frame.data()));frame+=payload;
    pending.insert(id,std::move(done));outgoing.enqueue(frame);
    if(socket.state()==QAbstractSocket::UnconnectedState) {
        sslFailureDetails.clear();tlsEstablished=false;
        QUrl endpoint("tcp://"+url->text().trimmed());
        if(endpoint.host().isEmpty()||endpoint.port()<1||endpoint.port()>65535){failPending("请输入有效的主机名:端口，例如 ops.example.com:29564");return;}
        socket.setProxy(QNetworkProxy::NoProxy);
        socket.setPeerVerifyMode(QSslSocket::VerifyPeer);socket.connectToHostEncrypted(endpoint.host(),static_cast<quint16>(endpoint.port()));
        connectTimeoutTimer.start();
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
    authenticated=value;operationControls->setEnabled(value);protectedControls->setEnabled(value);loginButton->setEnabled(!value);
    if(value){pages->setCurrentIndex(0);}else if(pages->currentIndex()==0&&pages->count()>1){pages->setCurrentIndex(1);refreshTimer.stop();}
}

void MainWindow::login() {
    if(hostPassword->text().isEmpty()){showError("请输入转发后端的服主密码");return;}
    {
        auto settings=appSettings();
        settings.setValue("credentials/remember",rememberCredentials->isChecked());
        if(rememberCredentials->isChecked()) {
            settings.setValue("credentials/serverAddress",url->text().trimmed());
            QByteArray protectedPassword;
            if(protectPassword(hostPassword->text(),protectedPassword))settings.setValue("credentials/passwordProtected",protectedPassword);
            else {
                settings.remove("credentials/passwordProtected");
                statusBar()->showMessage("无法安全保存密码；本次仍会尝试登录",7000);
            }
        } else {
            settings.remove("credentials/serverAddress");
            settings.remove("credentials/passwordProtected");
        }
    }
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
    request("metrics",{},[this](int code,const QByteArray& bytes){
        if(code!=200)return;auto j=QJsonDocument::fromJson(bytes).object();
        if(!j.value("available").toBool()){chart->append(-1,-1);return;}
        const double cpuValue=j.value("cpu_percent").toDouble();const double total=j.value("memory_total_bytes").toDouble(),used=j.value("memory_used_bytes").toDouble();
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
