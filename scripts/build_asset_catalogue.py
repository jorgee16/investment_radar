#!/usr/bin/env python3
"""Build a pinned, offline discovery index. No prices or investment recommendations.

Input: FinanceDatabase checkout at REVISION; preserves upstream descriptions and
identifiers. Excludes explicitly delisted rows and unsupported ticker syntax.
Run: python scripts/build_asset_catalogue.py /path/to/FinanceDatabase
"""
import csv, gzip, hashlib, json, re, sqlite3, subprocess, sys, tempfile, unicodedata, zlib
from pathlib import Path
REVISION = '6b5a1ae19bc87eb96c702ca760828411f4f3f14b'
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'android/app/src/main/assets/catalogue'
SECTORS = {'Industrials':'Indústria','Technology':'Tecnologia','Information Technology':'Tecnologia','Consumer Discretionary':'Consumo discricionário','Consumer Staples':'Consumo essencial','Financials':'Finanças','Health Care':'Saúde','Healthcare':'Saúde','Energy':'Energia','Materials':'Materiais','Basic Materials':'Materiais','Communication Services':'Comunicações','Utilities':'Serviços públicos','Real Estate':'Imobiliário'}
COUNTRIES = {'United States':'Estados Unidos','Netherlands':'Países Baixos','Germany':'Alemanha','France':'França','United Kingdom':'Reino Unido','Spain':'Espanha','Italy':'Itália','Switzerland':'Suíça','Belgium':'Bélgica','Austria':'Áustria','Denmark':'Dinamarca','Sweden':'Suécia','Norway':'Noruega','Finland':'Finlândia','Ireland':'Irlanda','Greece':'Grécia','Poland':'Polónia','Czech Republic':'Chéquia','China':'China','Japan':'Japão','South Korea':'Coreia do Sul','India':'Índia','Singapore':'Singapura','Hong Kong':'Hong Kong','Taiwan':'Taiwan','Canada':'Canadá','Brazil':'Brasil','Australia':'Austrália','South Africa':'África do Sul','Mexico':'México','New Zealand':'Nova Zelândia','Turkey':'Turquia','United Arab Emirates':'Emirados Árabes Unidos','Saudi Arabia':'Arábia Saudita','Luxembourg':'Luxemburgo'}
EUROPE = {'Portugal','Netherlands','Germany','France','United Kingdom','Spain','Italy','Switzerland','Belgium','Austria','Denmark','Sweden','Norway','Finland','Ireland','Greece','Poland','Czech Republic','Luxembourg','Hungary','Romania','Bulgaria','Estonia','Latvia','Lithuania','Iceland','Croatia','Slovenia','Slovakia','Malta','Cyprus'}
ASIA = {'China','Japan','South Korea','India','Singapore','Hong Kong','Taiwan','Malaysia','Indonesia','Philippines','Thailand','Vietnam','Pakistan','Bangladesh','Sri Lanka'}
def key(s):
 return ''.join(c for c in unicodedata.normalize('NFKD',s.lower()) if not unicodedata.combining(c))
def build(source):
 assert subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()==REVISION, 'Unexpected source revision'
 OUT.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory() as tmp:
  dbpath=Path(tmp)/'assets.sqlite'; db=sqlite3.connect(dbpath)
  db.executescript('''PRAGMA page_size=4096;
 CREATE TABLE assets(id INTEGER PRIMARY KEY,symbol TEXT UNIQUE,name TEXT,type TEXT,sector TEXT,currency TEXT,isin TEXT,country TEXT,exchange TEXT,region TEXT,market_cap TEXT,industry TEXT,website TEXT,issuer TEXT,description_id INTEGER);
 CREATE TABLE descriptions(id INTEGER PRIMARY KEY,text BLOB);
 CREATE VIRTUAL TABLE search USING fts4(tokens,content='',tokenize=unicode61);
 CREATE INDEX asset_filters ON assets(type,country,sector,market_cap);
 ''')
  descriptions={}; countries=set(); count={}; excluded=0; digests={}
  for kind in ['equities','etfs']:
   count[kind]=0
   for file in sorted((source/'database'/kind).glob('*.csv')):
    digests[str(file.relative_to(source))]=hashlib.sha256(file.read_bytes()).hexdigest()
    for row in csv.DictReader(file.open(encoding='utf-8')):
     symbol=row['symbol'].strip().upper(); name=row['name'].strip()
     if row.get('delisted','').lower()=='true' or not name or not re.fullmatch(r'[A-Z0-9][A-Z0-9._-]{0,31}',symbol):
      excluded+=1;continue
     raw_country=row.get('country',''); country=COUNTRIES.get(raw_country,raw_country)
     summary=row.get('summary','').strip()
     # This dataset's ETF bucket also contains notes/commodities: distinguish them.
     typ='Ação' if kind=='equities' else 'ETF'
     if kind=='etfs' and re.search(r'\b(ETC|ETN)\b|exchange.traded (commodity|note)',name+' '+summary,re.I):typ='ETC/ETN'
     if kind=='equities' and not row.get('sector') and not summary and re.search(r'\b(TURBO|WARRANT|CALL|PUT)\b|\d+[CP]\s+\d',name,re.I):
      excluded+=1;continue
     sector=SECTORS.get(row.get('sector',''),row.get('sector','')) if typ=='Ação' else 'ETF diversificados' if typ=='ETF' else 'Outros produtos'
     sector=sector or 'Sem setor'
     region='Europa' if raw_country in EUROPE else 'EUA' if raw_country=='United States' else 'Ásia' if raw_country in ASIA else 'Outras' if raw_country else 'Por confirmar'
     if kind=='etfs':region='Por confirmar' # Listing/issuer is not portfolio exposure.
     # Keep a readable original excerpt, with no machine-generated financial facts.
     if len(summary)>2400: summary=summary[:2400].rsplit(' ',1)[0]+'…'
     desc=None
     if summary:
      if summary not in descriptions:
       cursor=db.execute('INSERT INTO descriptions(text) VALUES(?)',(zlib.compress(summary.encode('utf-8'),9),)); descriptions[summary]=cursor.lastrowid
      desc=descriptions[summary]
     values=(symbol,name,typ,sector,row.get('currency',''),row.get('isin',''),country,row.get('market') or row.get('exchange',''),region,row.get('market_cap',''),row.get('industry',''),row.get('website',''),row.get('family',''),desc)
     cursor=db.execute('INSERT OR IGNORE INTO assets(symbol,name,type,sector,currency,isin,country,exchange,region,market_cap,industry,website,issuer,description_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',values)
     if cursor.rowcount==0:continue
     tokens=key(' '.join([symbol,name,row.get('isin',''),row.get('family',''),re.sub(r's\s*&\s*p\s*500','sp500 sp 500',name,flags=re.I)]))
     db.execute('INSERT INTO search(docid,tokens) VALUES(?,?)',(cursor.lastrowid,tokens))
     count[kind]+=1
     if country:countries.add(country)
  db.commit(); db.execute("INSERT INTO search(search) VALUES('optimize')");db.commit();db.execute('VACUUM');db.close()
  data=dbpath.read_bytes(); compressed=gzip.compress(data,compresslevel=9,mtime=0)
  (OUT/'assets.sqlite.bin').write_bytes(compressed)
  manifest={'source':'FinanceDatabase','sourceUrl':'https://github.com/JerBouma/FinanceDatabase','revision':REVISION,'snapshotDate':'2026-10-20','descriptionLanguage':'en','descriptionDates':'Individual descriptions have no observation dates; snapshot date is repository version only.','counts':count,'total':sum(count.values()),'countries':sorted(countries),'excluded':excluded,'sha256':hashlib.sha256(data).hexdigest(),'compressedBytes':len(compressed),'databaseBytes':len(data),'inputHashes':digests}
  (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
  (OUT/'FINANCEDATABASE-LICENSE.txt').write_bytes((source/'LICENSE').read_bytes())
  print(json.dumps({k:manifest[k] for k in ['counts','total','excluded','compressedBytes','databaseBytes']},indent=2))
if __name__=='__main__':build(Path(sys.argv[1]))
