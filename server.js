const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const sql = require('mssql');
const path = require('path');
const fs = require('fs');
const http = require('http');
const { exec } = require('child_process');

const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));

const crypto = require('crypto');

const app = express();
const ORIGENES_PERMITIDOS = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
app.use(cors({
  origin: (origen, cb) => cb(null, !origen || origen === 'null' || ORIGENES_PERMITIDOS.test(origen)),
}));
app.use(express.json({ limit: '10kb' }));
app.use((req, res, next) => {
  res.set('Cache-Control', 'no-store');
  next();
});

const dbConfig = {
  server: config.server,
  database: config.database,
  user: config.user,
  password: config.password,
  options: {
    encrypt: false,
    trustServerCertificate: true,
  },
  connectionTimeout: 5000,
  requestTimeout: 8000,
  pool: { max: 10, min: 0, idleTimeoutMillis: 20000 },
};

let poolPromise = null;
function obtenerPool() {
  if (!poolPromise) {
    poolPromise = sql.connect(dbConfig).catch(err => {
      poolPromise = null;
      throw err;
    });
  }
  return poolPromise;
}

async function buscarEmpleado(usuario) {
  const pool = await obtenerPool();
  const result = await pool
    .request()
    .input('usuario', sql.VarChar(50), usuario)
    .input('dni', sql.VarChar(50), usuario)
    .query(
      `SELECT TOP 1 Usuario, DNI, Nombres, ApellidoPaterno, ApellidoMaterno, password, esActivo
       FROM Empleados
       WHERE (Usuario = @usuario OR DNI = @dni)
         AND Usuario IS NOT NULL AND Usuario <> ''
         AND password IS NOT NULL AND password <> ''`
    );
  return result.recordset[0] || null;
}

const sesiones = new Map(); // token -> { usuario, expira }
const DURACION_SESION_MS = 8 * 60 * 60 * 1000;

function crearSesion(usuario) {
  const ahora = Date.now();
  for (const [t, sesion] of sesiones) {
    if (sesion.expira < ahora) sesiones.delete(t);
  }
  const token = crypto.randomBytes(32).toString('hex');
  sesiones.set(token, { usuario, expira: ahora + DURACION_SESION_MS });
  return token;
}

function requerirSesion(req, res, next) {
  const encabezado = req.get('Authorization') || '';
  const token = encabezado.startsWith('Bearer ') ? encabezado.slice(7) : '';
  const sesion = token ? sesiones.get(token) : null;
  if (!sesion || sesion.expira < Date.now()) {
    sesiones.delete(token);
    return res.status(401).json({ ok: false, mensaje: 'Su sesión venció. Inicie sesión nuevamente.' });
  }
  sesion.expira = Date.now() + DURACION_SESION_MS;
  next();
}

app.post('/api/login', async (req, res) => {
  const usuario = (req.body && req.body.usuario ? String(req.body.usuario) : '').trim();
  const clave = req.body && req.body.clave ? String(req.body.clave) : '';

  if (!usuario || !clave) {
    return res.status(400).json({ ok: false, mensaje: 'Ingrese usuario y contraseña.' });
  }

  try {
    const emp = await buscarEmpleado(usuario);
    if (!emp) {
      return res.status(401).json({ ok: false, mensaje: 'Credenciales incorrectas. Intente nuevamente.' });
    }
    if (emp.esActivo === 0) {
      return res.status(401).json({ ok: false, mensaje: 'Su cuenta está inactiva. Contacte al administrador.' });
    }
    if (!bcrypt.compareSync(clave, emp.password)) {
      return res.status(401).json({ ok: false, mensaje: 'Credenciales incorrectas. Intente nuevamente.' });
    }

    const token = crearSesion(emp.Usuario);
    res.json({
      ok: true,
      token,
      empleado: {
        usuario: emp.Usuario,
        dni: emp.DNI,
        nombre: [emp.Nombres, emp.ApellidoPaterno, emp.ApellidoMaterno].filter(Boolean).join(' '),
      },
    });
  } catch (err) {
    console.error('Error de login:', err.message);
    res.status(500).json({ ok: false, mensaje: 'No se pudo conectar a la base de datos.' });
  }
});

function calcularEdad(fechaNacimiento) {
  if (!fechaNacimiento) return null;
  const hoy = new Date();
  const nac = new Date(fechaNacimiento);
  let edad = hoy.getFullYear() - nac.getFullYear();
  const m = hoy.getMonth() - nac.getMonth();
  if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
  return edad;
}

function formatearNombre(p) {
  return [p.PrimerNombre, p.SegundoNombre, p.ApellidoPaterno, p.ApellidoMaterno]
    .filter(x => x && String(x).trim())
    .map(x => String(x).trim().toLowerCase().replace(/(^|\s)\S/g, c => c.toUpperCase()))
    .join(' ');
}

app.get('/api/pacientes', requerirSesion, async (req, res) => {
  const dni = req.query.dni ? String(req.query.dni).trim() : '';
  const hcRaw = req.query.hc ? String(req.query.hc).trim() : '';
  if (!dni && !hcRaw) {
    return res.status(400).json({ ok: false, mensaje: 'Indique un N° de DNI o N° de historia clínica.' });
  }
  const hcNum = parseInt(hcRaw.replace(/\D/g, ''), 10);
  if (hcRaw && isNaN(hcNum)) {
    return res.status(400).json({ ok: false, mensaje: 'El N° de historia clínica no es válido.' });
  }

  try {
    const pool = await obtenerPool();
    const peticion = pool.request();
    const condiciones = [];
    if (dni) {
      peticion.input('dni', sql.VarChar(12), dni);
      condiciones.push('p.NroDocumento = @dni');
    }
    if (hcRaw) {
      peticion.input('hc', sql.Int, hcNum);
      condiciones.push('p.NroHistoriaClinica = @hc');
    }

    const result = await peticion.query(
      `SELECT TOP 30 p.IdPaciente, p.ApellidoPaterno, p.ApellidoMaterno, p.PrimerNombre, p.SegundoNombre,
              p.NroDocumento, p.NroHistoriaClinica, p.FechaNacimiento,
              ts.Descripcion AS Sexo, tdi.Descripcion AS TipoDocumento
       FROM Pacientes p
       LEFT JOIN TiposSexo ts ON ts.IdTipoSexo = p.IdTipoSexo
       LEFT JOIN TiposDocIdentidad tdi ON tdi.IdDocIdentidad = p.IdDocIdentidad
       WHERE ` + condiciones.join(' OR ') + `
       ORDER BY p.ApellidoPaterno, p.ApellidoMaterno, p.PrimerNombre`
    );

    const pacientes = result.recordset.map(r => ({
      idPaciente: r.IdPaciente,
      dni: r.NroDocumento || '',
      tipoDocumento: r.TipoDocumento || 'DNI',
      nroHistoriaClinica: r.NroHistoriaClinica,
      nombre: formatearNombre(r),
      sexo: r.Sexo || '',
      edad: calcularEdad(r.FechaNacimiento),
    }));

    res.json({ ok: true, pacientes });
  } catch (err) {
    console.error('Error al buscar pacientes:', err.message);
    res.status(500).json({ ok: false, mensaje: 'No se pudo consultar la base de datos.' });
  }
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true, app: 'OxiKardex' });
});

// Solo se publican la página y sus imágenes. config.json y el código quedan privados.
const ARCHIVOS_PUBLICOS = new Set(['registro_oxigeno.html', 'imagenes.png', 'logoNoti.png', 'oxikardex-logo.svg']);

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'registro_oxigeno.html'));
});

app.get('/:archivo', (req, res, next) => {
  if (!ARCHIVOS_PUBLICOS.has(req.params.archivo)) return next();
  res.sendFile(path.join(__dirname, req.params.archivo));
});

function esServidorOxiKardex(puerto) {
  return new Promise(resolve => {
    const req = http.get(
      { host: '127.0.0.1', port: puerto, path: '/api/health', timeout: 1500 },
      res => {
        let data = '';
        res.on('data', c => (data += c));
        res.on('end', () => {
          try {
            resolve(JSON.parse(data).app === 'OxiKardex');
          } catch (e) {
            resolve(false);
          }
        });
      }
    );
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
  });
}

function abrirNavegador(puerto) {
  const url = 'http://localhost:' + puerto;
  exec('start "" "' + url + '"', err => {
    if (err) console.log('No se pudo abrir el navegador automáticamente. Abra ' + url + ' manualmente.');
  });
}

async function iniciarServidor() {
  const puertoFijo = config.port || 3000;

  // Si ya hay un servidor OxiKardex en el puerto fijo, esta ventana queda como respaldo
  // apuntando a http://localhost:3000 (sin crear otro servidor en otro puerto).
  if (await esServidorOxiKardex(puertoFijo)) {
    console.log('El servidor OxiKardex ya está corriendo en http://localhost:' + puertoFijo + '.');
    console.log('Esta ventana queda abierta como respaldo. Puede cerrarla cuando quiera.');
    console.log('Abriendo el navegador en http://localhost:' + puertoFijo + ' ...');
    abrirNavegador(puertoFijo);
    setInterval(() => {}, 1 << 30);
    return;
  }

  const servidor = app.listen(puertoFijo, config.host || '0.0.0.0', () => {
    console.log('Servidor OxiKardex iniciado en http://localhost:' + puertoFijo);
    console.log('Abriendo el navegador...');
    abrirNavegador(puertoFijo);
  });

  servidor.on('error', err => {
    if (err.code === 'EADDRINUSE') {
      console.error('El puerto ' + puertoFijo + ' está ocupado por otra aplicación.');
      console.error('Cierre esa aplicación o cambie el puerto en config.json y vuelva a iniciar.');
      process.exit(1);
    }
    throw err;
  });
}

iniciarServidor();
