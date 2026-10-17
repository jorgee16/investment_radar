package com.investmentradar.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.AllocationSlice
import com.investmentradar.desktop.model.HistoryPointDto
import kotlinx.coroutines.isActive
import kotlin.math.max

@Composable
fun Sparkline(values:List<Double>, positive:Boolean, modifier:Modifier=Modifier){
    if(values.size<2){Spacer(modifier);return}
    val color=if(positive) RadarColors.Positive else RadarColors.Negative
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
fun AreaLineChart(
    rows:List<HistoryPointDto>,
    currency:String,
    modifier:Modifier=Modifier
){
    val points=remember(rows){rows.filter{it.price!=null}.map{it.label to it.price!!}}
    var hover by remember{mutableStateOf<Int?>(null)}
    Box(modifier.background(RadarColors.Panel)){
        Canvas(
            Modifier.fillMaxSize().pointerInput(points){
                awaitPointerEventScope{
                    while(isActive){
                        val event=awaitPointerEvent()
                        val pos=event.changes.firstOrNull()?.position ?: continue
                        hover=if(points.isEmpty())null else ((pos.x/size.width)*(points.size-1)).toInt().coerceIn(points.indices)
                    }
                }
            }
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
            drawPath(path,RadarColors.Positive,style=Stroke(width=2.dp.toPx()))
            hover?.let{i->
                val x=i*dx
                drawLine(RadarColors.Crosshair,Offset(x,0f),Offset(x,size.height),1f)
            }
        }
        hover?.let{i->
            val p=points.getOrNull(i)?:return@let
            Column(
                Modifier.padding(12.dp).background(RadarColors.PanelRaised).padding(8.dp)
            ){
                Text(p.first,color=RadarColors.TextMuted,fontSize=10.sp)
                Text(FinancialFormatter.price(p.second,currency),color=RadarColors.Text,fontSize=12.sp)
            }
        }
    }
}

@Composable
fun AllocationDonut(slices:List<AllocationSlice>, modifier:Modifier=Modifier){
    if(slices.isEmpty()){
        Box(modifier,contentAlignment=androidx.compose.ui.Alignment.Center){
            Text("No portfolio allocation yet",color=RadarColors.TextMuted,fontSize=11.sp)
        }
        return
    }
    val total=slices.sumOf{it.value}.takeIf{it>0.0}?:1.0
    val colors=listOf(RadarColors.Accent,RadarColors.Positive,Color(0xFFE8BD66),Color(0xFFB389FF))
    var selected by remember{mutableStateOf<Int?>(null)}
    val progress by animateFloatAsState(1f,animationSpec=tween(450),label="allocation")
    Row(modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(22.dp)){
        Canvas(Modifier.size(160.dp)){
            var start=-90f
            slices.forEachIndexed{i,s->
                val sweep=(s.value/total*360.0).toFloat()*progress
                val width=if(selected==null||selected==i)22.dp.toPx() else 14.dp.toPx()
                drawArc(colors[i%colors.size],start,sweep,false,style=Stroke(width=width),size=Size(size.width,size.height))
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
                    Text("${s.label}  ${"%.1f".format(s.value/total*100)}%",color=if(selected==null||selected==i)RadarColors.Text else RadarColors.TextMuted,fontSize=11.sp)
                }
            }
        }
    }
}
