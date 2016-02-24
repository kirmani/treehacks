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
from flask import jsonify
from flask import request
from flask import current_app
from flask import url_for
from flask import make_response

BASE_DIR = os.path.dirname(os.path.realpath(__file__))
DATA_DIR = BASE_DIR + "/data"
ADFS_DIR = DATA_DIR + "/adfs"
SESSIONS_FILE = DATA_DIR + '/sessions.json'

SESSION_DATA_FILE = 'session_data.json'

app = Flask(__name__, static_url_path='')
app.secret_key = 'treehacks'

@app.route('/')
def Home():
  return 'Hello world!'

@app.route('/session/<session_id>/upload', methods=['POST'])
def file_upload(session_id):
  print "UPLOAD"
  print request.content_length
  if request.method == 'POST':
    file = request.files['adf']
    session = request.values['session']
    print session
    session_dir = DATA_DIR + "/" + session_id
    if not os.path.isdir(session_dir):
      os.mkdir(session_dir)
    adf_file = session_dir + "/adf"
    file.save(adf_file)
    serverdata = _LoadFile(SESSIONS_FILE)
    serverdata['sessions'][session]['adf'] = adf_file
    _WriteFile(serverdata, SESSIONS_FILE)
  return "Uploader"

@app.route('/download/<file>', methods=['GET'])
def download(file):
  headers = {"Content-Disposition": "attachment; filename=%s" % file}
  with open('/var/www/kirmani.io/treehacks/public_html/data/adfs/' + str(file), 'r') as f:
      body = f.read()
  return body

@app.route('/session/<session_id>', methods=['POST'])
def session(session_id):
  session_dir = DATA_DIR + "/" + session_id
  session_data_file = session_dir + "/" + SESSION_DATA_FILE
  if request.method == 'POST':
    if not os.path.isdir(session_dir):
      print("Creating session: " + session_id)
      os.mkdir(session_dir)
    else:
      return jsonify({"error": "Session already exists."})
    session_data = {
          'join_waiting': False,
          'devices': [],
        }
    _WriteFile(session_data, session_data_file)
    return jsonify(session_data)
  return jsonify({"error": "Unexpected error."})

def _LoadFile(filename):
  try:
    with open(filename, 'r+') as f:
      return json.load(fp=f)
  except IOError:
    print "Error"
    return {}

def _WriteFile(data, filename):
  j = json.dumps(data, indent=4)
  # print "WRITE: " + j
  #print "FILE: " + filename
  with open(filename, 'w+') as f:
    f.write(j)

if __name__ == '__main__':
  port = int(os.environ.get('PORT', 33507))
  app.run(host='0.0.0.0', port=port, debug=True)
