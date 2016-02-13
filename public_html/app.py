#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2015 Sean Kirmani <sean@kirmani.io>
#
# Distributed under terms of the MIT license.

import json
import os

from flask import Flask
from flask import request
from flask import current_app

DATAFILE = '/var/www/kirmani.io/treehacks/public_html/data.json'

app = Flask(__name__, static_url_path='')
app.secret_key = 'multi_tango_kirmani_io'

@app.before_request
def log_request():
  print request.headers
  print request.path
  print request.method
  print "DATA: " + request.data
  #print "JSON: " + request.json

@app.route('/')
def Home():
  return 'Hello world!'

@app.route('/session', defaults={'id': None}, methods=['GET','POST'])
@app.route('/session/<id>', methods=['GET','POST','PUT'])
def session(id):
  if id is None:
    if request.method == 'GET':
      return "{\"error\": \"Invalid request. You must use a POST request to create new sessions\"}"
    for key in request.values:
      print key + ": " + request.values[key]
    jsondata = request.json
    serverdata = _LoadFile(DATAFILE);
    if 'sessions' not in serverdata:
      serverdata['sessions'] = {}
    serverdata['sessions'][jsondata['name']] = jsondata['data']
    _WriteFile(serverdata, DATAFILE)
    return "{\"success\":true}" 
  else:
    id = str(id)
    if request.method == 'GET':
      serverdata = _LoadFile(DATAFILE)
      if id in serverdata['sessions']:
        return json.dumps(serverdata['sessions'][id])
    if request.method == 'PUT':
        jsondata = request.json
        serverdata = _LoadFile(DATAFILE)
        if 'devices' not in serverdata['sessions'][id]:
          serverdata['sessions'][id]['devices'] = {}
        for key in jsondata:
          if key not in serverdata['sessions'][id]:
            serverdata['sessions'][id][key] = {}
          serverdata['sessions'][id][key] = jsondata[key]
        _WriteFile(serverdata, DATAFILE)
    return 'Use existing session'
  return 'Session: %s' % id

def _LoadFile(filename):
  try:
    with open(filename, 'r+') as f:
      return json.load(fp=f)
  except IOError:
    print "Error"
    return {}

def _WriteFile(data, filename):
  j = json.dumps(data, indent=4)
  print "WRITE: " + j
  print "FILE: " + filename
  with open(filename, 'w+') as f:
    f.write(j)

if __name__ == '__main__':
  port = int(os.environ.get('PORT', 33507))
  app.run(host='0.0.0.0', port=port, debug=True)
