package com.investmentradar.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.AllocationSlice
import com.investmentradar.desktop.model.HistoryPointDto
import com.investmentradar.desktop.model.ChartStyle
import kotlin.math.max
import kotlin.math.min

@Composable
fun Sparkline(values:List<Double>,positive:Boolean,modifier:Modifier=Modifier){
    if(values.size<2){Spacer(modifier);return}
    val color=if(positive)RadarColors.Positive else RadarColors.Negative
    Canvas(modifier){
        val lo=values.minOrNull()?:return@Canvas
        val hi=values.maxOrNull()?:return@Canvas
        val span=(hi-lo).takeIf{it>0.0}?:1.0
        val dx=size.width/(values.size-1)
        val path=Path()
        values.forEachIndexed{i,v->
            val x=i*dx
            val y=size.height-(((v-lo)/span).toFloat()*size.height)
            if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path,color,style=Stroke(width=1.6.dp.toPx()))
    }
}

@Composable
fun MarketChart(
    rows:List<HistoryPointDto>,
    currency:String,
    style:ChartStyle,
    modifier:Modifier=Modifier
){
    val candles=remember(rows){rows.filter{it.open!=null&&it.high!=null&&it.low!=null&&it.close!=null}}
    when(style){
        ChartStyle.CANDLES -> if(candles.size>=2) CandlestickChart(candles,currency,modifier)
                              else AreaLineChart(rows,currency,modifier)
        ChartStyle.LINE -> AreaLineChart(rows,currency,modifier)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CandlestickChart(
    rows:List<HistoryPointDto>,
    currency:String,
    modifier:Modifier=Modifier
){
    var hover by remember(rows){mutableStateOf<Int?>(null)}
    var canvasSize by remember{mutableStateOf(IntSize.Zero)}
    Box(modifier.background(RadarColors.Panel)){
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged{canvasSize=it}
                .onPointerEvent(PointerEventType.Move){event->
                    val width=canvasSize.width.toFloat()
                    val x=event.changes.firstOrNull()?.position?.x
                    hover=if(rows.isEmpty()||width<=0f||x==null)null
                    else ((x/width)*rows.size).toInt().coerceIn(rows.indices)
                }
                .onPointerEvent(PointerEventType.Exit){hover=null}
        ){
            repeat(5){i->
                val y=size.height*i/4f
                drawLine(RadarColors.Grid,Offset(0f,y),Offset(size.width,y),1f)
            }
            if(rows.isEmpty())return@Canvas
            val hi=rows.maxOf{it.high!!}
            val lo=rows.minOf{it.low!!}
            val span=(hi-lo).takeIf{it>0.0}?:1.0
            val slot=size.width/rows.size
            val bodyWidth=max(2f,slot*0.58f)
            fun y(price:Double)=size.height-(((price-lo)/span).toFloat()*size.height)
            rows.forEachIndexed{i,row->
                val x=i*slot+slot/2f
                val openY=y(row.open!!)
                val closeY=y(row.close!!)
                val highY=y(row.high!!)
                val lowY=y(row.low!!)
                val up=row.close>=row.open
                val color=if(up)RadarColors.Positive else RadarColors.Negative
                drawLine(color,Offset(x,highY),Offset(x,lowY),1.2f)
                val top=min(openY,closeY)
                val bottom=max(openY,closeY)
                drawRect(
                    color=color,
                    topLeft=Offset(x-bodyWidth/2f,top),
                    size=Size(bodyWidth,max(1.5f,bottom-top))
                )
            }
            hover?.let{i->
                val x=i*slot+slot/2f
                drawLine(RadarColors.Crosshair,Offset(x,0f),Offset(x,size.height),1f)
            }
        }
        hover?.let{i->
            val row=rows.getOrNull(i)?:return@let
            ChartTooltip(
                title=row.label,
                lines=listOf(
                    "O" to FinancialFormatter.price(row.open,currency),
                    "H" to FinancialFormatter.price(row.high,currency),
                    "L" to FinancialFormatter.price(row.low,currency),
                    "C" to FinancialFormatter.price(row.close,currency)
                )
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AreaLineChart(rows:List<HistoryPointDto>,currency:String,modifier:Modifier=Modifier){
    val points=remember(rows){rows.filter{it.price!=null}.map{it.label to it.price!!}}
    var hover by remember(points){mutableStateOf<Int?>(null)}
    var canvasSize by remember{mutableStateOf(IntSize.Zero)}
    Box(modifier.background(RadarColors.Panel)){
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged{canvasSize=it}
                .onPointerEvent(PointerEventType.Move){event->
                    val width=canvasSize.width.toFloat()
                    val x=event.changes.firstOrNull()?.position?.x
                    hover=if(points.isEmpty()||width<=0f||x==null)null
                    else ((x/width)*(points.size-1)).toInt().coerceIn(points.indices)
                }
                .onPointerEvent(PointerEventType.Exit){hover=null}
        ){
            repeat(5){i->
                val y=size.height*i/4f
                drawLine(RadarColors.Grid,Offset(0f,y),Offset(size.width,y),1f)
            }
            if(points.size<2)return@Canvas
            val lo=points.minOf{it.second}
            val hi=points.maxOf{it.second}
            val span=(hi-lo).takeIf{it>0.0}?:1.0
            val dx=size.width/(points.size-1)
            val path=Path()
            points.forEachIndexed{i,p->
                val x=i*dx
                val y=size.height-(((p.second-lo)/span).toFloat()*size.height)
                if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            drawPath(path,RadarColors.Accent,style=Stroke(width=2.dp.toPx()))
            hover?.let{i->
                val x=i*dx
                drawLine(RadarColors.Crosshair,Offset(x,0f),Offset(x,size.height),1f)
            }
        }
        hover?.let{i->
            points.getOrNull(i)?.let{p->
                ChartTooltip(p.first,listOf("Price" to FinancialFormatter.price(p.second,currency)))
            }
        }
    }
}

@Composable
private fun ChartTooltip(title:String,lines:List<Pair<String,String>>){
    Column(
        Modifier.padding(12.dp)
            .background(RadarColors.PanelRaised)
            .padding(horizontal=10.dp,vertical=8.dp)
    ){
        Text(title,color=RadarColors.TextMuted,fontSize=9.sp)
        lines.forEach{(label,value)->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text(label,color=RadarColors.TextMuted,fontSize=9.sp)
                Text(value,color=RadarColors.Text,fontSize=10.sp)
            }
        }
    }
}

@Composable
fun AllocationDonut(slices:List<AllocationSlice>,modifier:Modifier=Modifier){
    if(slices.isEmpty()){
        Box(modifier,contentAlignment=androidx.compose.ui.Alignment.Center){
            Text("No portfolio positions yet",color=RadarColors.TextMuted,fontSize=11.sp)
        }
        return
    }
    val total=slices.sumOf{it.value}.takeIf{it>0.0}?:1.0
    val colors=listOf(RadarColors.Accent,RadarColors.Positive,Color(0xFFE8BD66),Color(0xFFB389FF))
    var selected by remember{mutableStateOf<Int?>(null)}
    val progress by animateFloatAsState(1f,animationSpec=tween(450),label="allocation")
    Row(modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(22.dp)){
        Canvas(Modifier.size(150.dp)){
            var start=-90f
            slices.forEachIndexed{i,s->
                val sweep=(s.value/total*360.0).toFloat()*progress
                val width=if(selected==null||selected==i)22.dp.toPx() else 14.dp.toPx()
                drawArc(colors[i%colors.size],start,sweep,false,style=Stroke(width=width))
                start+=sweep
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            slices.forEachIndexed{i,s->
                Row(
                    Modifier.clickable{selected=if(selected==i)null else i},
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ){
                    Box(Modifier.size(8.dp).background(colors[i%colors.size]))
                    Text(
                        "${s.label}  ${"%.1f".format(s.value/total*100)}%",
                        color=if(selected==null||selected==i)RadarColors.Text else RadarColors.TextMuted,
                        fontSize=11.sp
                    )
                }
            }
        }
    }
}
