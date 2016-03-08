# Vuzix M100向けWebRTCサンプル

## 使い方

### 準備（セッションIDの決定）
`MainActivity.java`を開き、定数`SESSION_ID`に適当な数字（セッションID）を入力します。

    private static final String SESSION_ID = "57815";


### 起動
アプリをビルドし、２つの端末で起動します。画面下部中央にセッションIDが同じであることを確認します。セッションIDが異なるアプリとは通信ができないので注意してください。

２つの端末でそれぞれ「READY」のボタンを押すと、「START」ボタンが押せるようになります。

もし「START」ボタンが押せない場合、セッションIDが異なるか、ネットワークと繋がっていないか、サーバーに接続できていない可能性があります。
セッションIDやお使いのM100の設定、ネットワークの設定を確認してください。

最後に、どちらか一方の端末で「START」ボタンを押すと、映像・音声通信が始まります（お使いのネットワークの速度によっては数分かかる場合があります）。

## License

    Copyright 2016 Vuzix Corporation

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


    Copyright (c) 2014, Ericsson AB. All rights reserved.

    Redistribution and use in source and binary forms, with or without modification,
    are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright notice, this
    list of conditions and the following disclaimer in the documentation and/or other
    materials provided with the distribution.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
    IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
    INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
    NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
    WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
    OF SUCH DAMAGE.
