package com.andre.infnethub.notificacao.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * O id do usuário autenticado.
 *
 * <p>Anotação própria, e não {@code @RequestHeader} ou {@code @AuthenticationPrincipal},
 * para que a origem da identidade fique num lugar só ({@link UsuarioAtualResolver}).
 * Os controllers pedem "quem é o usuário", sem saber de onde a resposta vem —
 * foi o que permitiu trocar o cabeçalho do TP3 pelo token do TP4 mexendo em um
 * arquivo.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioAtual {
}
