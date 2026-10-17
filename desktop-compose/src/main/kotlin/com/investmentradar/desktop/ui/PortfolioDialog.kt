package com.investmentradar.desktop.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.investmentradar.desktop.model.PortfolioPosition

@Composable fun AddPositionDialog(onDismiss:()->Unit,onAdd:(PortfolioPosition)->Unit){
 var symbol by remember{mutableStateOf("")}
 var qty by remember{mutableStateOf("")}
 var avg by remember{mutableStateOf("")}
 var currency by remember{mutableStateOf("EUR")}
 val q=qty.toDoubleOrNull();val a=avg.toDoubleOrNull()
 val valid=symbol.matches(Regex("[A-Z0-9.^:-]{1,32}"))&&q!=null&&q>0&&a!=null&&a>0&&currency.matches(Regex("[A-Z]{3}"))
 AlertDialog(onDismissRequest=onDismiss,title={Text("Add position")},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(symbol,{symbol=it.trim().uppercase()},label={Text("Ticker")},singleLine=true)
   OutlinedTextField(qty,{qty=it},label={Text("Quantity")},singleLine=true)
   OutlinedTextField(avg,{avg=it},label={Text("Average price")},singleLine=true)
   OutlinedTextField(currency,{currency=it.trim().uppercase()},label={Text("Quote currency")},singleLine=true)
  }
 },confirmButton={Button(enabled=valid,onClick={if(valid)onAdd(PortfolioPosition(symbol,q!!,a!!,currency))}){Text("Add")}},
 dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
