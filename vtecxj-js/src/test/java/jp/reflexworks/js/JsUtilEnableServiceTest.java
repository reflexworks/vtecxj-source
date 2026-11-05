package jp.reflexworks.js;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * サーバサイドJSの実行許可サービス判定テスト.
 */
public class JsUtilEnableServiceTest {

	private static Set<String> createSet(String... serviceNames) {
		Set<String> set = new HashSet<>();
		for (String serviceName : serviceNames) {
			set.add(serviceName);
		}
		return set;
	}

	@Test
	public void testEnabledService() {
		// 許可サービス = 実行可
		Set<String> enabledServices = createSet("service-a", "service-b");
		assertTrue(JsUtil.isEnabledService("service-a", enabledServices));
		assertTrue(JsUtil.isEnabledService("service-b", enabledServices));
	}

	@Test
	public void testNotEnabledService() {
		// 未許可サービス = 実行不可
		Set<String> enabledServices = createSet("service-a");
		assertFalse(JsUtil.isEnabledService("service-c", enabledServices));
		// 前方一致・部分一致では許可しない
		assertFalse(JsUtil.isEnabledService("service", enabledServices));
		assertFalse(JsUtil.isEnabledService("service-a2", enabledServices));
	}

	@Test
	public void testNoSetting() {
		// 設定なし = 全サービス実行不可
		assertFalse(JsUtil.isEnabledService("service-a", null));
		assertFalse(JsUtil.isEnabledService("service-a", Collections.emptySet()));
	}

	@Test
	public void testBlankServiceName() {
		Set<String> enabledServices = createSet("service-a");
		assertFalse(JsUtil.isEnabledService(null, enabledServices));
		assertFalse(JsUtil.isEnabledService("", enabledServices));
	}

}
