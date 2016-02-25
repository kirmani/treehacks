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

sessions = {}

@app.route('/')
def Home():
  return 'Hello world!'

@app.route('/session/<session_id>/upload', methods=['POST'])
def file_upload(session_id):
  adf = request.files['adf']
  session_dir = DATA_DIR + "/" + session_id
  if not os.path.isdir(session_dir):
    os.mkdir(session_dir)
  adf_file = session_dir + "/adf"
  adf.save(adf_file)
  sessions[session_id]['join_waiting'] = False
  return jsonify(sessions[session_id])

@app.route('/download/<file>', methods=['GET'])
def download(file):
  headers = {"Content-Disposition": "attachment; filename=%s" % file}
  with open('/var/www/kirmani.io/treehacks/public_html/data/adfs/' + str(file), 'r') as f:
      body = f.read()
  return body

@app.route('/session/<session_id>/join', methods=['POST'])
def SessionJoin(session_id):
  if session_id not in sessions:
    return jsonify({"error": "Session does not exist."})
  sessions[session_id]['join_waiting'] = True
  return sessions[session_id]

@app.route('/debug/sessions', methods=['GET'])
def DebugSessions():
  return jsonify(sessions)

@app.route('/session/<session_id>', methods=['POST', 'PUT', 'GET'])
def Session(session_id):
  if request.method == 'POST':
    if session_id in sessions:
      return jsonify({"error": "Session already exists."})
    print("Creating session: " + session_id)
    session_data = {
          'join_waiting': False,
          'devices': {},
        }
    sessions[session_id] = session_data
    return jsonify(session_data)
  if request.method == 'PUT':
    if session_id not in sessions:
      return jsonify({"error": "Session does not exist."})
    request_devices = request.json['devices']
    print(request_devices)
    for uuid in request_devices:
      sessions[session_id]['devices'][uuid] = request_devices[uuid]
    return jsonify(sessions[session_id])
  if request.method == 'GET':
    if session_id not in sessions:
      return jsonify({"error": "Session does not exist."})
    return jsonify(sessions[session_id])
  return jsonify({"error": "Unexpected error."})

if __name__ == '__main__':
  port = int(os.environ.get('PORT', 33507))
  app.run(host='0.0.0.0', port=port, debug=True)
