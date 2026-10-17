package com.investmentradar.desktop.viewmodel

import com.investmentradar.desktop.data.LocalStateStore
import com.investmentradar.desktop.data.MarketRepository
import com.investmentradar.desktop.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RadarViewModel(
    private val repository:MarketRepository,
    private val localStore:LocalStateStore
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val _state=MutableStateFlow(
        RadarUiState(
            watchSymbols=localStore.loadWatchlist(),
            portfolio=localStore.loadPortfolio()
        )
    )
    val state:StateFlow<RadarUiState> = _state.asStateFlow()

    init{
        refresh()
        startPriceLoop()
    }

    fun dispatch(intent:RadarIntent){
        when(intent){
            is RadarIntent.SelectTicker -> select(intent.symbol)
            is RadarIntent.Navigate -> navigate(intent.page)
            is RadarIntent.SelectAssetTab -> selectTab(intent.tab)
            is RadarIntent.AddWatch -> addWatch(intent.symbol)
            is RadarIntent.RemoveWatch -> removeWatch(intent.symbol)
            is RadarIntent.AddPosition -> addPosition(intent.position)
            is RadarIntent.RemovePosition -> removePosition(intent.index)
            is RadarIntent.SaveProviders -> saveProviders(intent)
            is RadarIntent.TestProvider -> testProvider(intent.provider,intent.symbol)
            RadarIntent.Refresh -> refresh()
        }
    }

    private fun refresh(){
        scope.launch{
            _state.update{it.copy(loading=true,error=null)}
            runCatching{
                val status=async{repository.status()}
                val providers=async{repository.providers()}
                val providerSettings=async{repository.providerSettings()}
                val earnings=async{repository.earnings()}
                val watch=loadWatchItems(_state.value.watchSymbols)
                val selected=repository.snapshot(_state.value.selectedSymbol)
                Quint(status.await(),providers.await(),providerSettings.await(),earnings.await(),watch) to selected
            }.onSuccess{(meta,selected)->
                _state.update{
                    it.copy(
                        loading=false,
                        status=meta.a,
                        providers=meta.b,
                        providerSettings=meta.c,
                        earnings=meta.d,
                        watchlist=meta.e,
                        selected=selected
                    )
                }
                loadAssetExtras(selected.symbol)
            }.onFailure{e->
                _state.update{it.copy(loading=false,error=e.message?:"Unknown error")}
            }
        }
    }

    private suspend fun loadWatchItems(symbols:List<String>):List<WatchItem> = coroutineScope {
        symbols.map{symbol->
            async(Dispatchers.IO){
                val quote=runCatching{repository.quote(symbol)}.getOrNull()
                val history=runCatching{repository.history(symbol,60)}.getOrDefault(emptyList())
                WatchItem(
                    symbol=symbol,
                    name=quote?.name?.ifBlank{symbol} ?: symbol,
                    price=quote?.price,
                    currency=quote?.currency.orEmpty(),
                    changePct=quote?.changePct,
                    sparkline=history.mapNotNull{it.price}
                )
            }
        }.awaitAll()
    }

    private fun select(symbolRaw:String){
        val symbol=symbolRaw.trim().uppercase()
        if(!symbol.matches(Regex("[A-Z0-9.^:-]{1,32}"))){
            _state.update{it.copy(error="Invalid ticker")}
            return
        }
        _state.update{it.copy(selectedSymbol=symbol,page=AppPage.RADAR,loading=true,error=null)}
        scope.launch{
            runCatching{repository.snapshot(symbol)}
                .onSuccess{snapshot->
                    _state.update{it.copy(selected=snapshot,loading=false)}
                    loadAssetExtras(symbol)
                }
                .onFailure{e->_state.update{it.copy(loading=false,error=e.message)}}
        }
    }

    private fun loadAssetExtras(symbol:String){
        scope.launch{
            val earnings=async{runCatching{repository.earnings(symbol)}.getOrDefault(emptyList())}
            val news=async{runCatching{repository.news(symbol)}.getOrDefault(emptyList())}
            _state.update{it.copy(assetEarnings=earnings.await(),news=news.await())}
        }
    }

    private fun navigate(page:AppPage){
        _state.update{it.copy(page=page,error=null)}
        if(page==AppPage.PROVIDERS) loadProviders()
        if(page==AppPage.EVENTS) loadEvents()
        if(page==AppPage.SCREENER) loadWatchAnalysis()
    }

    private fun selectTab(tab:AssetTab){
        _state.update{it.copy(selectedTab=tab)}
        val symbol=_state.value.selectedSymbol
        if(tab==AssetTab.NEWS || tab==AssetTab.EARNINGS) loadAssetExtras(symbol)
    }

    private fun addWatch(symbolRaw:String){
        val symbol=symbolRaw.trim().uppercase()
        if(!symbol.matches(Regex("[A-Z0-9.^:-]{1,32}"))) return
        val next=(_state.value.watchSymbols+symbol).distinct()
        localStore.saveWatchlist(next)
        _state.update{it.copy(watchSymbols=next)}
        scope.launch{
            val items=loadWatchItems(next)
            _state.update{it.copy(watchlist=items)}
        }
    }

    private fun removeWatch(symbol:String){
        val next=_state.value.watchSymbols.filterNot{it==symbol}
        localStore.saveWatchlist(next)
        _state.update{it.copy(watchSymbols=next,watchlist=it.watchlist.filterNot{row->row.symbol==symbol})}
    }

    private fun addPosition(position:PortfolioPosition){
        if(position.qty<=0 || position.avg<=0 || !position.currency.matches(Regex("[A-Z]{3}"))) return
        val next=_state.value.portfolio+position.copy(symbol=position.symbol.trim().uppercase(),currency=position.currency.uppercase())
        localStore.savePortfolio(next)
        _state.update{it.copy(portfolio=next)}
    }

    private fun removePosition(index:Int){
        val rows=_state.value.portfolio.toMutableList()
        if(index !in rows.indices) return
        rows.removeAt(index)
        localStore.savePortfolio(rows)
        _state.update{it.copy(portfolio=rows)}
    }

    private fun loadWatchAnalysis(){
        scope.launch{
            val current=_state.value.watchlist
            val enriched=current.map{item->
                async(Dispatchers.IO){
                    val snap=runCatching{repository.snapshot(item.symbol)}.getOrNull()
                    if(snap==null)item else item.copy(score=snap.score,zone=snap.zone)
                }
            }.awaitAll()
            _state.update{it.copy(watchlist=enriched)}
        }
    }

    private fun loadEvents(){
        scope.launch{
            runCatching{repository.earnings()}
                .onSuccess{rows->_state.update{it.copy(earnings=rows)}}
                .onFailure{e->_state.update{it.copy(error=e.message)}}
        }
    }

    private fun loadProviders(){
        scope.launch{
            runCatching{
                Triple(repository.status(),repository.providers(),repository.providerSettings())
            }.onSuccess{(status,providers,settings)->
                _state.update{it.copy(status=status,providers=providers,providerSettings=settings)}
            }.onFailure{e->_state.update{it.copy(error=e.message)}}
        }
    }

    private fun saveProviders(intent:RadarIntent.SaveProviders){
        scope.launch{
            _state.update{it.copy(savingProviders=true,error=null)}
            runCatching{
                repository.saveProviderSettings(
                    intent.demo,intent.eodhdKey,intent.fmpKey,intent.twelveKey,intent.routing
                )
            }.onSuccess{
                val status=repository.status()
                val providers=repository.providers()
                _state.update{state->
                    state.copy(
                        savingProviders=false,
                        providerSettings=it,
                        status=status,
                        providers=providers
                    )
                }
                refresh()
            }.onFailure{e->_state.update{it.copy(savingProviders=false,error=e.message)}}
        }
    }

    private fun testProvider(provider:String,symbolRaw:String){
        val symbol=symbolRaw.trim().uppercase().ifBlank{"AAPL"}
        scope.launch{
            runCatching{repository.testProvider(provider,symbol)}
                .onSuccess{reports->
                    _state.update{state->
                        state.copy(providerTests=state.providerTests+reports.associateBy{it.id})
                    }
                    loadProviders()
                }
                .onFailure{e->_state.update{it.copy(error=e.message)}}
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
                            item.copy(
                                price=q.price,
                                currency=q.currency,
                                changePct=q.changePct,
                                name=q.name.ifBlank{item.name}
                            )
                        } ?: item
                    }
                }.awaitAll()
                _state.update{it.copy(watchlist=updated)}
            }
        }
    }

    override fun close(){scope.cancel()}

    private data class Quint<A,B,C,D,E>(val a:A,val b:B,val c:C,val d:D,val e:E)
}
