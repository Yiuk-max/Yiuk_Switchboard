#include "loadchart.h"
#include <QPainter>
#include <QPainterPath>
#include <algorithm>

LoadChart::LoadChart(QWidget* parent) : QWidget(parent) {
    setMinimumHeight(190);
    setSizePolicy(QSizePolicy::Expanding,QSizePolicy::Preferred);
}

void LoadChart::append(double cpuPercent,double memoryPercent) {
    cpu.push_back(cpuPercent); memory.push_back(memoryPercent);
    if(cpu.size()>60)cpu.remove(0);if(memory.size()>60)memory.remove(0);
    update();
}

void LoadChart::paintEvent(QPaintEvent*) {
    QPainter p(this);p.setRenderHint(QPainter::Antialiasing);
    p.fillRect(rect(),QColor("#2B313F"));
    // Reserve separate rows for the time labels and centered legend.
    const QRectF plot(48,14,width()-66,height()-62);
    if(plot.width()<20||plot.height()<20)return;
    p.setPen(QPen(QColor("#50586A"),1));
    for(int i=0;i<=4;i++) {
        const qreal y=plot.bottom()-plot.height()*i/4.0;p.drawLine(QPointF(plot.left(),y),QPointF(plot.right(),y));
        p.setPen(QColor("#E2E7BF"));p.drawText(QRectF(2,y-9,40,18),Qt::AlignRight|Qt::AlignVCenter,QString::number(i*25)+"%");p.setPen(QPen(QColor("#50586A"),1));
    }
    p.setPen(QColor("#E2E7BF"));p.drawText(QRectF(plot.left(),plot.bottom()+8,70,18),"5 min ago");p.drawText(QRectF(plot.right()-36,plot.bottom()+8,38,18),"now");
    auto drawSeries=[&](const QVector<double>& values,const QColor& color){
        if(values.size()<2)return;QPainterPath path;bool started=false;
        for(qsizetype i=0;i<values.size();++i){const double value=values[i];if(value<0){started=false;continue;}const qreal x=plot.left()+plot.width()*i/(values.size()-1);const qreal y=plot.bottom()-plot.height()*std::clamp(value,0.0,100.0)/100.0;if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);}
        p.setPen(QPen(color,2));p.drawPath(path);
    };
    const QColor cpuColor("#89B9FF"),memoryColor("#B8D986");
    drawSeries(cpu,cpuColor);drawSeries(memory,memoryColor);
    const QFontMetrics fm(p.font());
    const int markerWidth=16,itemGap=6,groupGap=22;
    const int cpuTextWidth=fm.horizontalAdvance("CPU"),memoryTextWidth=fm.horizontalAdvance("内存");
    const int groupWidth=markerWidth+itemGap+cpuTextWidth+groupGap+markerWidth+itemGap+memoryTextWidth;
    const int groupLeft=(width()-groupWidth)/2;
    const int legendBaseline=height()-8,markerY=legendBaseline-5;
    p.setPen(QPen(cpuColor,3));p.drawLine(groupLeft,markerY,groupLeft+markerWidth,markerY);
    p.setPen(QColor("#E2E7BF"));p.drawText(groupLeft+markerWidth+itemGap,legendBaseline,"CPU");
    const int memoryLeft=groupLeft+markerWidth+itemGap+cpuTextWidth+groupGap;
    p.setPen(QPen(memoryColor,3));p.drawLine(memoryLeft,markerY,memoryLeft+markerWidth,markerY);
    p.setPen(QColor("#E2E7BF"));p.drawText(memoryLeft+markerWidth+itemGap,legendBaseline,"内存");
}
