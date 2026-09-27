package com.andre.infnethub.service.impl;

import com.andre.infnethub.dto.CurtidaResponseDTO;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.mensageria.EventosDoFeed;
import com.andre.infnethub.model.Curtida;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.CurtidaRepository;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.service.CurtidaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurtidaServiceImpl implements CurtidaService {

    private final CurtidaRepository  curtidaRepository;
    private final PostRepository     postRepository;
    private final UsuarioRepository  usuarioRepository;
    private final EventosDoFeed      eventos;

    @Override
    @Transactional(readOnly = true)
    public List<CurtidaResponseDTO> listarPorPost(Long postId) {
        return curtidaRepository.findByPostId(postId)
                .stream().map(CurtidaResponseDTO::fromEntity).toList();
    }

    @Override
    @Transactional
    public ResultadoCurtida alternar(Long postId, Long usuarioId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado: " + postId));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + usuarioId));

        Optional<Curtida> existente = curtidaRepository.findByPostIdAndUsuarioId(postId, usuarioId);

        boolean curtido;
        if (existente.isPresent()) {
            curtidaRepository.delete(existente.get());
            curtido = false;
        } else {
            curtidaRepository.save(Curtida.builder().post(post).usuario(usuario).build());
            curtido = true;
            // Só a curtida vira evento; o descurtir não interessa a ninguém fora
            // daqui. Mesma transação: se a curtida não for gravada, a
            // notificação de curtida também não existe.
            eventos.curtido(post, usuario);
        }

        // Recontar e regravar o contador desnormalizado é o ponto central deste
        // método. No TP1 o toggle criava e removia a linha em `curtidas` mas
        // deixava `posts.curtidas` intocado: a contagem exibida no feed voltava
        // ao valor antigo assim que a página era recarregada. Contar aqui, na
        // mesma transação da escrita, mantém o derivado fiel à origem.
        long total = curtidaRepository.countByPostId(postId);
        post.setCurtidas((int) total);
        postRepository.save(post);

        return new ResultadoCurtida(curtido, total);
    }
}
