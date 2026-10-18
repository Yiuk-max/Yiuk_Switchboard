#include "loadchart.h"
#include <QPainter>
#include <QPainterPath>
#include <QMouseEvent>
#include <algorithm>

LoadChart::LoadChart(QWidget* parent) : QWidget(parent) {
    setMinimumHeight(190);
    setSizePolicy(QSizePolicy::Expanding,QSizePolicy::Preferred);
    setMouseTracking(true);
}

void LoadChart::append(double cpuPercent,double memoryPercent) {
    cpu.push_back(cpuPercent); memory.push_back(memoryPercent);
    if(cpu.size()>60)cpu.remove(0);if(memory.size()>60)memory.remove(0);
    if(hoveredIndex>=cpu.size())hoveredIndex=cpu.size()-1;
    update();
}

void LoadChart::mouseMoveEvent(QMouseEvent* event) {
    const QRectF plot(48,14,width()-66,height()-62);
    if(cpu.isEmpty()||plot.width()<20||!plot.contains(event->position()))hoveredIndex=-1;
    else hoveredIndex=cpu.size()==1?0:qBound(0,qRound((event->position().x()-plot.left())/plot.width()*(cpu.size()-1)),cpu.size()-1);
    update();
}

void LoadChart::leaveEvent(QEvent*) { hoveredIndex=-1;update(); }

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
    if(hoveredIndex>=0&&hoveredIndex<cpu.size()) {
        const qreal x=cpu.size()==1?plot.left():plot.left()+plot.width()*hoveredIndex/(cpu.size()-1);
        p.setPen(QPen(QColor("#E2E7BF"),1,Qt::DashLine));p.drawLine(QPointF(x,plot.top()),QPointF(x,plot.bottom()));
        auto drawPoint=[&](const QVector<double>& values,const QColor& color){
            if(values[hoveredIndex]<0)return;
            const qreal y=plot.bottom()-plot.height()*std::clamp(values[hoveredIndex],0.0,100.0)/100.0;
            p.setPen(QPen(QColor("#2B313F"),2));p.setBrush(color);p.drawEllipse(QPointF(x,y),4,4);
        };
        drawPoint(cpu,cpuColor);drawPoint(memory,memoryColor);
        const auto valueText=[](double value){return value<0?QString("不可用"):QString::number(value,'f',1)+"%";};
        const int secondsAgo=(cpu.size()-1-hoveredIndex)*5;
        const QStringList lines={QString("%1 前").arg(secondsAgo==0?QString("现在"):QString("%1 秒").arg(secondsAgo)),
                                 QString("CPU  %1").arg(valueText(cpu[hoveredIndex])),
                                 QString("内存  %1").arg(valueText(memory[hoveredIndex]))};
        const int pad=8,lineHeight=18,boxWidth=150,boxHeight=pad*2+lineHeight*lines.size();
        const qreal boxX=std::min(x+6.0,static_cast<qreal>(width()-boxWidth-4));
        const QRectF box(boxX,plot.top()+6,boxWidth,boxHeight);
        p.setPen(QPen(QColor("#747D90"),1));p.setBrush(QColor("#202530"));p.drawRoundedRect(box,5,5);
        p.setPen(QColor("#E2E7BF"));for(int i=0;i<lines.size();++i)p.drawText(QRectF(box.left()+pad,box.top()+pad+i*lineHeight,box.width()-2*pad,lineHeight),Qt::AlignLeft|Qt::AlignVCenter,lines[i]);
    }
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
