package com.investmentradar.desktop.viewmodel

import com.investmentradar.desktop.data.MarketRepository
import com.investmentradar.desktop.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RadarViewModel(
    private val repository:MarketRepository,
    private val watchSymbols:List<String> = listOf("AIR.PA","VUAA.DE","6AQQ.DE","JPM","GS","TSM")
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val _state=MutableStateFlow(RadarUiState())
    val state:StateFlow<RadarUiState> = _state.asStateFlow()
    init{refresh();startPriceLoop()}

    fun dispatch(intent:RadarIntent)=when(intent){
        is RadarIntent.SelectTicker -> select(intent.symbol)
        RadarIntent.Refresh -> refresh()
    }

    private fun refresh(){
        scope.launch{
            _state.update{it.copy(loading=true,error=null)}
            runCatching{
                val status=repository.status()
                val items=watchSymbols.map{symbol->async{
                    val quote=repository.quote(symbol)
                    val snap=runCatching{repository.snapshot(symbol)}.getOrNull()
                    WatchItem(symbol,quote.name.ifBlank{symbol},quote.price,quote.currency,quote.changePct,snap?.history?.mapNotNull{it.price}.orEmpty())
                }}.awaitAll()
                Triple(status,items,repository.snapshot(_state.value.selectedSymbol))
            }.onSuccess{(status,items,selected)->_state.update{it.copy(loading=false,status=status,watchlist=items,selected=selected)}}
             .onFailure{e->_state.update{it.copy(loading=false,error=e.message?:"Unknown error")}}
        }
    }

    private fun select(symbol:String){
        _state.update{it.copy(selectedSymbol=symbol,loading=true,error=null)}
        scope.launch{
            runCatching{repository.snapshot(symbol)}
                .onSuccess{snapshot->_state.update{it.copy(selected=snapshot,loading=false)}}
                .onFailure{e->_state.update{it.copy(loading=false,error=e.message)}}
        }
    }


    private fun startPriceLoop(){
        scope.launch{
            while(isActive){
                delay(5000)
                val current=_state.value
                if(current.watchlist.isEmpty()) continue
                val updated=current.watchlist.map{item->
                    async(Dispatchers.IO){
                        runCatching{repository.quote(item.symbol)}.getOrNull()?.let{q->
                            item.copy(price=q.price,currency=q.currency,changePct=q.changePct,name=q.name.ifBlank{item.name})
                        } ?: item
                    }
                }.awaitAll()
                _state.update{it.copy(watchlist=updated)}
            }
        }
    }

    override fun close(){scope.cancel()}
}
