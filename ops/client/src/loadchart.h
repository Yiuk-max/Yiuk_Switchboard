#pragma once
#include <QWidget>
#include <QVector>

class LoadChart final : public QWidget {
public:
    explicit LoadChart(QWidget* parent = nullptr);
    void append(double cpuPercent, double memoryPercent);
protected:
    void paintEvent(QPaintEvent* event) override;
private:
    QVector<double> cpu, memory;
};
