# Android Jocket使用文档


## 导入方法

- 将[Android Jocket](https://github.com/ninty90/AndroidJocket)从github上clone到本地

- 将clone下来的项目作为你的项目的moudle引入

- 由于项目中用到了[DaVinci](https://github.com/ninty90/DaVinci)所以需要在你的全局的build.gradle加入

  ```
  repositories{
      maven { 
      	url "https://jitpack.io" 
      }
  }
  ```

## 使用方法

- 创建Jocket Session发起HTTP GET请求，生成Jocket的Session ID。Jocket的Session和HTTP的Session类似，但不是同一内容。URL支持两种格式：

  ```
  http://<host>[:port]/<app-name><path>.jocket
  http://<host>[:port]/<app-name>/create.jocket?jocket_path=<path>
  ```

  代码通过以下方式实现：

  ```java
  Jocket jocket = new Jocket(baseUrl);
  jocket.connect(path, map, onJocketListener);
  ```

具体实现方式参考[**Jocket – Polling 工作步骤.docx**](https://github.com/ninty90/AndroidJocket/blob/master/Jocket%20%E2%80%93%20Polling%20%E5%B7%A5%E4%BD%9C%E6%AD%A5%E9%AA%A4.docx)
