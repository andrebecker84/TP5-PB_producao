package com.andre.infnethub.service.impl;

import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.exception.ConflitoDeDadosException;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.expurgo.SagaDeExpurgo;
import com.andre.infnethub.mensageria.EventosDeUsuario;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.service.UsuarioService;
import com.andre.infnethub.seguranca.PapeisDoKeycloak;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EventosDeUsuario eventos;
    private final SagaDeExpurgo saga;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findByRemovidoFalse()
                .stream()
                .map(UsuarioResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarPorPapel(Papel papel) {
        return usuarioRepository.findByPapelAndRemovidoFalse(papel)
                .stream()
                .map(UsuarioResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> buscar(String termo) {
        return usuarioRepository.buscarPorTermo(termo)
                .stream()
                .map(UsuarioResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findByIdAndRemovidoFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com id: " + id));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        // Antecipa a constraint uk_usuarios_email para devolver 409 com mensagem
        // de domínio, em vez de deixar a violação subir crua do banco. A checagem
        // não é atômica — duas requisições simultâneas passam juntas por aqui —
        // e por isso a constraint continua sendo a garantia final; o
        // GlobalExceptionHandler traduz quem perder a corrida.
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new ConflitoDeDadosException("Já existe um usuário cadastrado com o e-mail: " + dto.email());
        }
        Usuario criado = usuarioRepository.save(dto.toEntity());
        // Mesma transação do INSERT: se ele voltar atrás, o evento volta junto.
        eventos.cadastrado(criado);
        return UsuarioResponseDTO.fromEntity(criado);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByIdAndRemovidoFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com id: " + id));

        // O e-mail pode permanecer o mesmo: só é conflito se já pertencer a outro usuário.
        usuarioRepository.findByEmail(dto.email())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflitoDeDadosException("O e-mail " + dto.email() + " já pertence a outro usuário.");
                });

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setEscola(dto.escola());
        usuario.setUltimoBloco(dto.ultimoBloco());
        usuario.setClasse(dto.classe());
        // saveAndFlush, e não save: o UPDATE precisa ir ao banco agora para que
        // o @Version já esteja incrementado quando o evento for montado. Com o
        // flush adiado para o commit, o evento sairia com a versão anterior, e
        // o consumidor o descartaria como velho.
        Usuario atualizado = usuarioRepository.saveAndFlush(usuario);
        eventos.atualizado(atualizado);
        return UsuarioResponseDTO.fromEntity(atualizado);
    }

    /**
     * Abre a saga de expurgo — não apaga nada aqui.
     *
     * <h2>O que mudou em relação ao TP3, e por quê</h2>
     * <p>Antes, este método fazia um {@code DELETE} e pronto. Duas coisas
     * estavam erradas nisso:
     * <ul>
     *   <li>um usuário com posts ou comentários era <strong>barrado pelas chaves
     *       estrangeiras</strong>, e a remoção respondia 409. Quem publicou no
     *       feed simplesmente não podia ser removido — o que não é uma resposta
     *       aceitável a um pedido de eliminação;</li>
     *   <li>os dados replicados nos outros serviços ficavam para trás, porque
     *       nada garantia que eles tivessem cumprido a sua parte.</li>
     * </ul>
     *
     * <p>A saga resolve os dois: o passo final é uma <em>anonimização</em>, que
     * não esbarra em chave estrangeira nenhuma, e ela só acontece depois de os
     * participantes confirmarem. Ver {@link SagaDeExpurgo}.
     */
    @Override
    @Transactional
    public void deletar(Long id) {
        saga.solicitar(id, autorDaRequisicao());
    }

    /**
     * Quem pediu, lido do token. Nulo quando a chamada não vem de uma
     * requisição autenticada — um teste, por exemplo —, e nesse caso ninguém é
     * avisado se a saga for revertida.
     */
    private Long autorDaRequisicao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao instanceof JwtAuthenticationToken token
                ? PapeisDoKeycloak.usuarioIdDe(token.getToken())
                : null;
    }

    /**
     * Uma transação para o lote inteiro: ou todos os eventos entram na caixa de
     * saída, ou nenhum. O volume é o do cadastro de usuários de uma instituição
     * — milhares, não milhões —, e o relay os publica em lotes a partir daí.
     *
     * <p>Vai como {@code UsuarioAtualizadoV1}, com a versão atual, e não como um
     * tipo novo: o evento já carrega o estado completo, que é exatamente o que
     * o consumidor precisa para se sincronizar. Quem já tem essa versão a
     * descarta; quem não tem, aplica.
     */
    @Override
    @Transactional
    public int reenviarEstadoAtual() {
        // Só quem está ativo. Reenviar o estado de quem foi anonimizado
        // espalharia de novo, pelo broker, os dados que a saga de expurgo
        // acabou de tirar de circulação.
        List<Usuario> ativos = usuarioRepository.findByRemovidoFalse();
        ativos.forEach(eventos::atualizado);
        return ativos.size();
    }
}
