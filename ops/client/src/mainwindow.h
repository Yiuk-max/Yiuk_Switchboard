#pragma once
#include <QMainWindow>
#include <QSslSocket>
#include <QTimer>
#include <QHash>
#include <QQueue>
#include <QVector>
#include <QJsonObject>
#include <functional>

class QLineEdit; class QLabel; class QPushButton; class QSpinBox; class QWidget; class QTabWidget; class LoadChart;
class MainWindow : public QMainWindow {
    Q_OBJECT
public: explicit MainWindow(QWidget* parent = nullptr);
private:
    QSslSocket socket; QTimer refreshTimer; bool authenticated = false; QByteArray incoming;
    quint32 nextRequestId = 1; QHash<quint32,std::function<void(int,const QByteArray&)>> pending;
    QQueue<QByteArray> outgoing;
    QLineEdit *url, *hostPassword, *hostPasswordConfig, *playerPasswordConfig, *logFile;
    QSpinBox *controlPort, *roomPortMin, *roomPortMax, *maxRooms, *maxPlayersPerRoom, *maxPendingPerRoom;
    QSpinBox *pendingTimeoutSec, *reconnectGraceSec, *heartbeatTimeoutSec, *sessionIdleTimeoutSec, *maxConnections, *authRatePerMin;
    QLabel *processStatus, *cpuStatus, *memoryStatus; QPushButton* loginButton;
    QTabWidget* pages; QWidget* protectedControls; LoadChart* chart;
    void login(); void request(const QString& type, const QByteArray& body,
                               std::function<void(int,const QByteArray&)> done);
    void command(const QString& action); void refresh(); void loadConfig(); void saveConfig();
    void setConfig(const QJsonObject& config); QJsonObject configFields() const; void setAuthenticated(bool value);
    void showError(const QString& message);
    void flushRequests(); void readResponses(); void failPending(const QString& error);
};
