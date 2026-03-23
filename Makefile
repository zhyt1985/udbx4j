JAVA17_HOME ?= /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home

.PHONY: test test-all verify clean compile package run-test coverage

## 编译主代码
compile:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn compile'

## 运行 Spec 测试（不含集成测试）
test:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn test'

## 运行所有测试（含集成测试）+ 覆盖率报告
test-all:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn verify'

## 清理构建产物
clean:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn clean'

## 打包（跳过测试）
package:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn package -DskipTests'

## 运行单个测试类
## 用法：make run-test CLASS=GaiaPointSpecTest
run-test:
	sh -c 'export JAVA_HOME="$(JAVA17_HOME)" && export PATH="$$JAVA_HOME/bin:$$PATH" && mvn test -Dtest=$(CLASS)'

## 查看覆盖率报告（需先运行 make test-all）
coverage:
	open target/site/jacoco/index.html
